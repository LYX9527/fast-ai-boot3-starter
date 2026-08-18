package io.github.lyx9527.fastai.autoconfigure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lyx9527.fastai.persistence.JdbcAiStorageSchema;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 SQLite 或 MySQL 的 Spring AI 对话历史持久化仓库。
 */
final class JdbcChatMemoryRepository implements ChatMemoryRepository {

    /** 消息 metadata 的 JSON 反序列化类型。 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    /** Tool Call 和 Tool Response 列表的 JSON 反序列化类型。 */
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {
    };

    /** 数据库访问模板。 */
    private final JdbcTemplate jdbc;
    /** 消息 metadata 和 Tool 数据的 JSON 序列化器。 */
    private final ObjectMapper objectMapper;
    /** 保证会话窗口替换写入原子性的事务模板。 */
    private final TransactionTemplate transaction;

    JdbcChatMemoryRepository(DataSource dataSource, ObjectMapper objectMapper) {
        JdbcAiStorageSchema.initialize(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public List<String> findConversationIds() {
        return this.jdbc.queryForList(
                "SELECT DISTINCT conversation_id FROM fast_ai_chat_memory ORDER BY conversation_id", String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return this.jdbc.query("""
                SELECT message_type, content, metadata_json, tool_calls_json, tool_responses_json
                FROM fast_ai_chat_memory
                WHERE conversation_id = ?
                ORDER BY message_index
                """, (resultSet, rowNumber) -> deserialize(resultSet), conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        this.transaction.executeWithoutResult(status -> {
            this.jdbc.update("DELETE FROM fast_ai_chat_memory WHERE conversation_id = ?", conversationId);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            this.jdbc.batchUpdate("""
                    INSERT INTO fast_ai_chat_memory
                        (conversation_id, message_index, message_type, content, metadata_json,
                         tool_calls_json, tool_responses_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(java.sql.PreparedStatement statement, int index) throws SQLException {
                    MessageRecord record = serialize(messages.get(index));
                    statement.setString(1, conversationId);
                    statement.setLong(2, index);
                    statement.setString(3, record.messageType());
                    statement.setString(4, record.content());
                    statement.setString(5, record.metadataJson());
                    statement.setString(6, record.toolCallsJson());
                    statement.setString(7, record.toolResponsesJson());
                    statement.setTimestamp(8, Timestamp.from(Instant.now()));
                }

                @Override
                public int getBatchSize() {
                    return messages.size();
                }
            });
        });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        this.jdbc.update("DELETE FROM fast_ai_chat_memory WHERE conversation_id = ?", conversationId);
    }

    private MessageRecord serialize(Message message) {
        String toolCalls = null;
        String toolResponses = null;
        if (message instanceof AssistantMessage assistant) {
            toolCalls = json(assistant.getToolCalls().stream()
                    .map(JdbcChatMemoryRepository::toolCallMap)
                    .toList());
        }
        else if (message instanceof ToolResponseMessage toolResponse) {
            toolResponses = json(toolResponse.getResponses().stream()
                    .map(JdbcChatMemoryRepository::toolResponseMap)
                    .toList());
        }
        return new MessageRecord(message.getMessageType().getValue(), message.getText(), json(message.getMetadata()),
                toolCalls, toolResponses);
    }

    private Message deserialize(ResultSet resultSet) throws SQLException {
        String type = resultSet.getString("message_type");
        String content = resultSet.getString("content");
        Map<String, Object> metadata = map(resultSet.getString("metadata_json"));
        MessageType messageType = MessageType.fromValue(type);
        return switch (messageType) {
            case USER -> UserMessage.builder().text(content == null ? "" : content).metadata(metadata).build();
            case SYSTEM -> SystemMessage.builder().text(content == null ? "" : content).metadata(metadata).build();
            case ASSISTANT -> AssistantMessage.builder()
                    .content(content == null ? "" : content)
                    .properties(metadata)
                    .toolCalls(toolCalls(resultSet.getString("tool_calls_json")))
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(toolResponses(resultSet.getString("tool_responses_json")))
                    .metadata(metadata)
                    .build();
        };
    }

    private List<AssistantMessage.ToolCall> toolCalls(String value) {
        return mapList(value).stream()
                .map(item -> new AssistantMessage.ToolCall(string(item, "id"), string(item, "type"),
                        string(item, "name"), string(item, "arguments")))
                .toList();
    }

    private List<ToolResponseMessage.ToolResponse> toolResponses(String value) {
        return mapList(value).stream()
                .map(item -> new ToolResponseMessage.ToolResponse(string(item, "id"), string(item, "name"),
                        string(item, "responseData")))
                .toList();
    }

    private List<Map<String, Object>> mapList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return this.objectMapper.readValue(value, LIST_MAP_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chat tool metadata", exception);
        }
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return this.objectMapper.readValue(value, MAP_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chat message metadata", exception);
        }
    }

    private String json(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value == null ? Map.of() : value);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chat message", exception);
        }
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private static Map<String, Object> toolCallMap(AssistantMessage.ToolCall call) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", call.id());
        values.put("type", call.type());
        values.put("name", call.name());
        values.put("arguments", call.arguments());
        return values;
    }

    private static Map<String, Object> toolResponseMap(ToolResponseMessage.ToolResponse response) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", response.id());
        values.put("name", response.name());
        values.put("responseData", response.responseData());
        return values;
    }

    /**
     * 单条消息写入数据库前的序列化结果。
     *
     * @param messageType 消息类型
     * @param content 消息文本
     * @param metadataJson 消息 metadata JSON
     * @param toolCallsJson Tool Call JSON
     * @param toolResponsesJson Tool Response JSON
     */
    private record MessageRecord(String messageType, String content, String metadataJson,
            String toolCallsJson, String toolResponsesJson) {
    }
}
