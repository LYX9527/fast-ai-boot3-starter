package io.github.lyx9527.fastai.context;

import io.github.lyx9527.fastai.persistence.JdbcAiStorageSchema;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 基于 SQLite 或 MySQL 的会话累计 Token 用量存储。
 */
public final class JdbcAiConversationUsageStore implements AiConversationUsageStore {

    /** 数据库访问模板。 */
    private final JdbcTemplate jdbc;

    public JdbcAiConversationUsageStore(DataSource dataSource) {
        JdbcAiStorageSchema.initialize(dataSource);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public AiConversationUsage get(String conversationKey) {
        List<AiConversationUsage> usages = this.jdbc.query("""
                SELECT cumulative_prompt_tokens, cumulative_completion_tokens,
                       cumulative_total_tokens, request_count
                FROM fast_ai_conversation_usage
                WHERE conversation_id = ?
                """, (resultSet, rowNumber) -> new AiConversationUsage(
                resultSet.getLong("cumulative_prompt_tokens"),
                resultSet.getLong("cumulative_completion_tokens"),
                resultSet.getLong("cumulative_total_tokens"),
                resultSet.getLong("request_count")), conversationKey);
        return usages.isEmpty() ? AiConversationUsage.empty() : usages.get(0);
    }

    @Override
    public AiConversationUsage add(String conversationKey, Integer promptTokens,
            Integer completionTokens, Integer totalTokens) {
        long prompt = positive(promptTokens);
        long completion = positive(completionTokens);
        long total = positive(totalTokens);
        if (total == 0) {
            total = prompt + completion;
        }
        Timestamp now = Timestamp.from(Instant.now());
        int updated = update(conversationKey, prompt, completion, total, now);
        if (updated == 0) {
            try {
                this.jdbc.update("""
                        INSERT INTO fast_ai_conversation_usage
                            (conversation_id, cumulative_prompt_tokens, cumulative_completion_tokens,
                             cumulative_total_tokens, request_count, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, conversationKey, prompt, completion, total, 1L, now);
            }
            catch (DuplicateKeyException exception) {
                update(conversationKey, prompt, completion, total, now);
            }
        }
        return get(conversationKey);
    }

    @Override
    public void clear(String conversationKey) {
        this.jdbc.update("DELETE FROM fast_ai_conversation_usage WHERE conversation_id = ?", conversationKey);
    }

    private int update(String conversationKey, long promptTokens, long completionTokens,
            long totalTokens, Timestamp updatedAt) {
        return this.jdbc.update("""
                UPDATE fast_ai_conversation_usage
                SET cumulative_prompt_tokens = cumulative_prompt_tokens + ?,
                    cumulative_completion_tokens = cumulative_completion_tokens + ?,
                    cumulative_total_tokens = cumulative_total_tokens + ?,
                    request_count = request_count + 1,
                    updated_at = ?
                WHERE conversation_id = ?
                """, promptTokens, completionTokens, totalTokens, updatedAt, conversationKey);
    }

    private static long positive(Integer value) {
        return value == null ? 0 : Math.max(0, value.longValue());
    }
}
