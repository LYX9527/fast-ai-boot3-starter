package io.github.lyx9527.fastai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.lyx9527.fastai.memory.AiMemoryItem;
import io.github.lyx9527.fastai.memory.AiMemoryScope;
import io.github.lyx9527.fastai.memory.JdbcAiLongTermMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcAiPersistenceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void persistsCompleteChatHistoryAcrossRepositoryInstances() {
        try (HikariDataSource dataSource = dataSource("chat-history.db")) {
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            JdbcChatMemoryRepository first = new JdbcChatMemoryRepository(dataSource, objectMapper);
            AssistantMessage assistant = AssistantMessage.builder()
                    .content("")
                    .properties(Map.of("provider", "test"))
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            "call-1", "function", "order.query", "{\"orderNo\":\"1001\"}")))
                    .build();
            ToolResponseMessage toolResponse = ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            "call-1", "order.query", "{\"status\":\"PAID\"}")))
                    .metadata(Map.of("traceId", "trace-1"))
                    .build();
            first.saveAll("conversation-1", List.of(
                    new SystemMessage("system"),
                    UserMessage.builder().text("query order").metadata(Map.of("channel", "web")).build(),
                    assistant,
                    toolResponse));

            JdbcChatMemoryRepository second = new JdbcChatMemoryRepository(dataSource, objectMapper);
            List<Message> restored = second.findByConversationId("conversation-1");

            assertThat(second.findConversationIds()).containsExactly("conversation-1");
            assertThat(restored).hasSize(4);
            assertThat(restored.get(1).getText()).isEqualTo("query order");
            assertThat(restored.get(1).getMetadata()).containsEntry("channel", "web");
            assertThat(((AssistantMessage) restored.get(2)).getToolCalls()).containsExactly(
                    new AssistantMessage.ToolCall("call-1", "function", "order.query",
                            "{\"orderNo\":\"1001\"}"));
            assertThat(((ToolResponseMessage) restored.get(3)).getResponses()).containsExactly(
                    new ToolResponseMessage.ToolResponse("call-1", "order.query",
                            "{\"status\":\"PAID\"}"));
        }
    }

    @Test
    void persistsLongTermMemoryAcrossStoreInstances() {
        try (HikariDataSource dataSource = dataSource("long-term-memory.db")) {
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            JdbcAiLongTermMemoryStore first = new JdbcAiLongTermMemoryStore(dataSource, objectMapper);
            first.save(new AiMemoryItem("memory-1", "tenant-a", "user-a", "用户喜欢深色模式",
                    "preference", "conversation-1", Instant.now(), null, Map.of("source", "manual")));

            JdbcAiLongTermMemoryStore second = new JdbcAiLongTermMemoryStore(dataSource, objectMapper);
            List<AiMemoryItem> restored = second.search(
                    new AiMemoryScope("tenant-a", "user-a", "conversation-2"), "深色模式", 5);

            assertThat(restored).hasSize(1);
            assertThat(restored.get(0).content()).isEqualTo("用户喜欢深色模式");
            assertThat(restored.get(0).metadata()).containsEntry("source", "manual");

            second.deleteByUser("tenant-a", "user-a");
            assertThat(new JdbcAiLongTermMemoryStore(dataSource, objectMapper).search(
                    new AiMemoryScope("tenant-a", "user-a", "conversation-3"), "深色模式", 5)).isEmpty();
        }
    }

    private HikariDataSource dataSource(String fileName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + this.tempDirectory.resolve(fileName));
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
