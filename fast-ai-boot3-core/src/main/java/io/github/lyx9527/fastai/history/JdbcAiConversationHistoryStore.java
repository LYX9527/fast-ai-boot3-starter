package io.github.lyx9527.fastai.history;

import io.github.lyx9527.fastai.memory.AiMemoryScope;
import io.github.lyx9527.fastai.persistence.JdbcAiStorageSchema;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 基于 SQLite 或 MySQL 的追加式完整对话历史存储。
 */
public final class JdbcAiConversationHistoryStore implements AiConversationHistoryStore {

    /** 数据库访问模板。 */
    private final JdbcTemplate jdbc;
    /** 保证同一轮用户和助手消息同时写入的事务模板。 */
    private final TransactionTemplate transaction;

    public JdbcAiConversationHistoryStore(DataSource dataSource) {
        JdbcAiStorageSchema.initialize(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void appendTurn(AiMemoryScope scope, String conversationKey,
            String userMessage, String assistantMessage) {
        Instant now = Instant.now();
        String turnId = sortableTurnId(now);
        List<HistoryRecord> records = new ArrayList<>(2);
        records.add(new HistoryRecord(UUID.randomUUID().toString(), turnId, 0, "user", userMessage, now));
        if (assistantMessage != null && !assistantMessage.isBlank()) {
            records.add(new HistoryRecord(UUID.randomUUID().toString(), turnId, 1,
                    "assistant", assistantMessage, now));
        }
        this.transaction.executeWithoutResult(status -> this.jdbc.batchUpdate("""
                INSERT INTO fast_ai_conversation_history
                    (id, conversation_key, tenant_id, user_id, conversation_id, turn_id,
                     message_order, message_type, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                HistoryRecord record = records.get(index);
                statement.setString(1, record.id());
                statement.setString(2, conversationKey);
                statement.setString(3, scope.tenantId());
                statement.setString(4, scope.userId());
                statement.setString(5, scope.conversationId());
                statement.setString(6, record.turnId());
                statement.setInt(7, record.messageOrder());
                statement.setString(8, record.messageType());
                statement.setString(9, record.content());
                statement.setTimestamp(10, Timestamp.from(record.createdAt()));
            }

            @Override
            public int getBatchSize() {
                return records.size();
            }
        }));
    }

    @Override
    public List<AiConversationHistoryMessage> findByConversation(AiMemoryScope scope) {
        return this.jdbc.query("""
                SELECT id, conversation_key, turn_id, tenant_id, user_id, conversation_id,
                       message_order, message_type, content, created_at
                FROM fast_ai_conversation_history
                WHERE tenant_id = ? AND user_id = ? AND conversation_id = ?
                ORDER BY turn_id, message_order
                """, (resultSet, rowNumber) -> new AiConversationHistoryMessage(
                resultSet.getString("id"),
                resultSet.getString("conversation_key"),
                resultSet.getString("turn_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                resultSet.getString("conversation_id"),
                resultSet.getInt("message_order"),
                resultSet.getString("message_type"),
                resultSet.getString("content"),
                resultSet.getTimestamp("created_at").toInstant()),
                scope.tenantId(), scope.userId(), scope.conversationId());
    }

    @Override
    public void clear(AiMemoryScope scope) {
        this.jdbc.update("""
                DELETE FROM fast_ai_conversation_history
                WHERE tenant_id = ? AND user_id = ? AND conversation_id = ?
                """, scope.tenantId(), scope.userId(), scope.conversationId());
    }

    /** 单条历史消息写入数据库前的内部结构。 */
    private record HistoryRecord(String id, String turnId, int messageOrder,
            String messageType, String content, Instant createdAt) {
    }

    /**
     * 生成带纳秒时间前缀的轮次标识，避免 MySQL TIMESTAMP 精度不足时打乱同秒内的对话顺序。
     */
    private static String sortableTurnId(Instant createdAt) {
        long epochNanos = Math.addExact(Math.multiplyExact(createdAt.getEpochSecond(), 1_000_000_000L),
                createdAt.getNano());
        return String.format(Locale.ROOT, "%019d-%s", epochNanos, UUID.randomUUID());
    }
}
