package io.github.lyx9527.fastai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lyx9527.fastai.persistence.JdbcAiStorageSchema;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * 基于 JDBC 的用户长期记忆存储。
 * 相关度评分在 Java 内完成，使 SQLite 和 MySQL 无需依赖厂商特定全文检索语法即可共用实现。
 */
public final class JdbcAiLongTermMemoryStore implements AiLongTermMemoryStore {

    /** 长期记忆 metadata 的 JSON 反序列化类型。 */
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    /** 数据库访问模板。 */
    private final JdbcTemplate jdbc;
    /** metadata JSON 序列化器。 */
    private final ObjectMapper objectMapper;
    /** 保证去重删除与新增写入原子性的事务模板。 */
    private final TransactionTemplate transaction;

    public JdbcAiLongTermMemoryStore(DataSource dataSource, ObjectMapper objectMapper) {
        JdbcAiStorageSchema.initialize(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void save(AiMemoryItem memory) {
        this.transaction.executeWithoutResult(status -> {
            List<AiMemoryItem> existing = findForUser(memory.tenantId(), memory.userId());
            String normalized = normalize(memory.content());
            existing.stream()
                    .filter(item -> normalize(item.content()).equals(normalized) || item.id().equals(memory.id()))
                    .map(AiMemoryItem::id)
                    .forEach(id -> this.jdbc.update("DELETE FROM fast_ai_long_term_memory WHERE id = ?", id));
            this.jdbc.update("""
                    INSERT INTO fast_ai_long_term_memory
                        (id, tenant_id, user_id, content, memory_type, source_conversation_id,
                         created_at, expires_at, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, memory.id(), memory.tenantId(), memory.userId(), memory.content(), memory.memoryType(),
                    memory.sourceConversationId(), timestamp(memory.createdAt()), timestamp(memory.expiresAt()),
                    json(memory.metadata()));
        });
    }

    @Override
    public List<AiMemoryItem> search(AiMemoryScope scope, String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Instant now = Instant.now();
        List<AiMemoryItem> memories = findForUser(scope.tenantId(), scope.userId());
        List<String> expired = memories.stream()
                .filter(memory -> memory.expired(now))
                .map(AiMemoryItem::id)
                .toList();
        expired.forEach(id -> this.jdbc.update("DELETE FROM fast_ai_long_term_memory WHERE id = ?", id));

        return memories.stream()
                .filter(memory -> !memory.expired(now))
                .map(memory -> new ScoredMemory(memory, score(query, memory.content())))
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                        .thenComparing(item -> item.memory().createdAt(), Comparator.reverseOrder()))
                .limit(limit)
                .map(ScoredMemory::memory)
                .toList();
    }

    @Override
    public void deleteByUser(String tenantId, String userId) {
        this.jdbc.update("DELETE FROM fast_ai_long_term_memory WHERE tenant_id = ? AND user_id = ?", tenantId, userId);
    }

    private List<AiMemoryItem> findForUser(String tenantId, String userId) {
        return this.jdbc.query("""
                SELECT id, tenant_id, user_id, content, memory_type, source_conversation_id,
                       created_at, expires_at, metadata_json
                FROM fast_ai_long_term_memory
                WHERE tenant_id = ? AND user_id = ?
                """, (resultSet, rowNumber) -> map(resultSet), tenantId, userId);
    }

    private AiMemoryItem map(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new AiMemoryItem(resultSet.getString("id"), resultSet.getString("tenant_id"),
                resultSet.getString("user_id"), resultSet.getString("content"), resultSet.getString("memory_type"),
                resultSet.getString("source_conversation_id"), createdAt == null ? null : createdAt.toInstant(),
                expiresAt == null ? null : expiresAt.toInstant(), jsonMap(resultSet.getString("metadata_json")));
    }

    private String json(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value == null ? Map.of() : value);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize AI memory metadata", exception);
        }
    }

    private Map<String, Object> jsonMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return this.objectMapper.readValue(value, METADATA_TYPE);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize AI memory metadata", exception);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static double score(String query, String content) {
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(content);
        if (normalizedQuery.isBlank()) {
            return 0;
        }
        double score = normalizedContent.contains(normalizedQuery) || normalizedQuery.contains(normalizedContent)
                ? 10 : 0;
        Set<Integer> queryCharacters = codePoints(normalizedQuery);
        Set<Integer> contentCharacters = codePoints(normalizedContent);
        if (!queryCharacters.isEmpty()) {
            queryCharacters.retainAll(contentCharacters);
            score += (double) queryCharacters.size() / Math.max(1, codePoints(normalizedQuery).size());
        }
        return score;
    }

    private static Set<Integer> codePoints(String value) {
        Set<Integer> result = new HashSet<>();
        value.codePoints().filter(Character::isLetterOrDigit).forEach(result::add);
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    /**
     * 带相关度分数的记忆内部模型。
     *
     * @param memory 长期记忆
     * @param score 相关度分数
     */
    private record ScoredMemory(AiMemoryItem memory, double score) {
    }
}
