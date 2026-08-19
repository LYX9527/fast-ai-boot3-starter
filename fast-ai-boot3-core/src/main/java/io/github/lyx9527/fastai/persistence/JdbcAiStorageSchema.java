package io.github.lyx9527.fastai.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 创建 starter 使用的持久化表和索引。
 * 建表 SQL 仅使用 SQLite 与 MySQL 均支持的语法。
 */
public final class JdbcAiStorageSchema {

    private JdbcAiStorageSchema() {
    }

    public static void initialize(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS fast_ai_chat_memory (
                    conversation_id VARCHAR(255) NOT NULL,
                    message_index BIGINT NOT NULL,
                    message_type VARCHAR(32) NOT NULL,
                    content TEXT,
                    metadata_json TEXT NOT NULL,
                    tool_calls_json TEXT,
                    tool_responses_json TEXT,
                    created_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (conversation_id, message_index)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS fast_ai_long_term_memory (
                    id VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(255) NOT NULL,
                    user_id VARCHAR(255) NOT NULL,
                    content TEXT NOT NULL,
                    memory_type VARCHAR(64) NOT NULL,
                    source_conversation_id VARCHAR(255),
                    created_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP NULL,
                    metadata_json TEXT NOT NULL,
                    PRIMARY KEY (id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS fast_ai_conversation_usage (
                    conversation_id VARCHAR(255) NOT NULL,
                    cumulative_prompt_tokens BIGINT NOT NULL,
                    cumulative_completion_tokens BIGINT NOT NULL,
                    cumulative_total_tokens BIGINT NOT NULL,
                    request_count BIGINT NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (conversation_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS fast_ai_conversation_history (
                    id VARCHAR(128) NOT NULL,
                    conversation_key VARCHAR(255) NOT NULL,
                    tenant_id VARCHAR(255) NOT NULL,
                    user_id VARCHAR(255) NOT NULL,
                    conversation_id VARCHAR(255) NOT NULL,
                    turn_id VARCHAR(128) NOT NULL,
                    message_order INTEGER NOT NULL,
                    message_type VARCHAR(32) NOT NULL,
                    content TEXT,
                    created_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (id)
                )
                """);
        createIndexIfMissing(dataSource, jdbc, "fast_ai_long_term_memory",
                "idx_fast_ai_long_term_user", "tenant_id, user_id, created_at");
        createIndexIfMissing(dataSource, jdbc, "fast_ai_conversation_history",
                "idx_fast_ai_history_scope", "tenant_id, user_id, turn_id");
        createIndexIfMissing(dataSource, jdbc, "fast_ai_conversation_history",
                "idx_fast_ai_history_key", "conversation_key, turn_id");
    }

    private static void createIndexIfMissing(DataSource dataSource, JdbcTemplate jdbc, String table,
            String index, String columns) {
        if (indexExists(dataSource, table, index)) {
            return;
        }
        try {
            jdbc.execute("CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
        }
        catch (DataAccessException exception) {
            if (!indexExists(dataSource, table, index)) {
                throw exception;
            }
        }
    }

    private static boolean indexExists(DataSource dataSource, String table, String index) {
        try (Connection connection = dataSource.getConnection();
                ResultSet indexes = connection.getMetaData().getIndexInfo(
                        connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                String existing = indexes.getString("INDEX_NAME");
                if (existing != null && existing.equalsIgnoreCase(index)) {
                    return true;
                }
            }
            return false;
        }
        catch (SQLException exception) {
            throw new IllegalStateException("Failed to inspect AI storage schema", exception);
        }
    }
}
