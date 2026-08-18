package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.chat.AiChatRequest;
import io.github.lyx9527.fastai.chat.AiChatService;
import io.github.lyx9527.fastai.chat.AiStreamEventType;
import io.github.lyx9527.fastai.memory.AiConversationKeyFactory;
import io.github.lyx9527.fastai.memory.AiLongTermMemoryStore;
import io.github.lyx9527.fastai.memory.AiMemoryScope;
import io.github.lyx9527.fastai.memory.JdbcAiLongTermMemoryStore;
import io.github.lyx9527.fastai.tool.AiToolContextValues;
import io.github.lyx9527.fastai.tool.AiToolRegistry;
import io.github.lyx9527.fastai.tool.AiToolSecurityEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FastAiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastAiAutoConfiguration.class))
            .withBean(ChatModel.class, StubChatModel::new)
            .withBean(ChatClient.Builder.class, () -> ChatClient.builder(new StubChatModel()))
            .withPropertyValues(
                    "fast.ai.memory.long-term.auto-extract=false",
                    "fast.ai.intent.enabled=false",
                    "fast.ai.persistence.sqlite.file=build/test-fast-ai-" + UUID.randomUUID() + ".db");

    @Test
    void providesInjectableChatService() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiChatService.class);
            assertThat(context.getBean(ChatMemoryRepository.class)).isInstanceOf(JdbcChatMemoryRepository.class);
            assertThat(context.getBean(AiLongTermMemoryStore.class))
                    .isInstanceOf(JdbcAiLongTermMemoryStore.class);
            AiChatService service = context.getBean(AiChatService.class);
            var response = service.chat(AiChatRequest.builder()
                    .message("hello")
                    .userId("u1")
                    .conversationId("c1")
                    .build());
            assertThat(response.content()).isEqualTo("stub-response");
            assertThat(response.contextUsage().actualPromptTokens()).isEqualTo(42);
            String memoryKey = context.getBean(AiConversationKeyFactory.class)
                    .create(new AiMemoryScope("default", "u1", "c1"));
            assertThat(context.getBean(ChatMemoryRepository.class).findByConversationId(memoryKey))
                    .extracting(message -> message.getText())
                    .contains("hello", "stub-response");
        });
    }

    @Test
    void exposesContextOccupancyInStreamingEvents() {
        this.contextRunner.run(context -> {
            AiChatService service = context.getBean(AiChatService.class);
            var events = service.stream(AiChatRequest.builder()
                            .message("hello")
                            .userId("u1")
                            .conversationId("stream-c1")
                            .build())
                    .collectList()
                    .block();

            assertThat(events).isNotNull().hasSize(3);
            assertThat(events.get(0).eventType()).isEqualTo(AiStreamEventType.CONTEXT);
            assertThat(events.get(0).contextUsage()).isNotNull();
            assertThat(events.get(1).eventType()).isEqualTo(AiStreamEventType.DELTA);
            assertThat(events.get(1).content()).isEqualTo("stub-response");
            assertThat(events.get(2).eventType()).isEqualTo(AiStreamEventType.COMPLETE);
            assertThat(events.get(2).finalChunk()).isTrue();
            assertThat(events.get(2).contextUsage().actualPromptTokens()).isEqualTo(42);
        });
    }

    @Test
    void exposesOverridableToolSecurityEvaluator() {
        AiToolSecurityEvaluator customEvaluator = request -> {
        };
        this.contextRunner
                .withBean("customToolSecurityEvaluator", AiToolSecurityEvaluator.class, () -> customEvaluator)
                .run(context -> assertThat(context.getBean(AiToolSecurityEvaluator.class))
                        .isSameAs(customEvaluator));
    }

    @Test
    void trustedToolContextValuesOverrideRequestMetadata() {
        AiChatRequest request = AiChatRequest.builder()
                .message("delete order")
                .tenantId("tenant-a")
                .userId("user-a")
                .conversationId("conversation-a")
                .addPermission("order:delete")
                .confirmTool("order.delete")
                .metadata(Map.of(
                        AiToolContextValues.TENANT_ID, "attacker-tenant",
                        AiToolContextValues.PERMISSIONS, Set.of("admin:*"),
                        AiToolContextValues.CONFIRMED_TOOLS, Set.of("*")))
                .build();

        Map<String, Object> context = DefaultAiChatService.toolContext(
                new AiMemoryScope("tenant-a", "user-a", "conversation-a"), request);

        assertThat(context.get(AiToolContextValues.TENANT_ID)).isEqualTo("tenant-a");
        assertThat(context.get(AiToolContextValues.PERMISSIONS)).isEqualTo(Set.of("order:delete"));
        assertThat(context.get(AiToolContextValues.CONFIRMED_TOOLS)).isEqualTo(Set.of("order.delete"));
        assertThat(context.get(AiToolContextValues.REQUEST_METADATA)).isEqualTo(request.metadata());
    }

    @Test
    void forwardsRequestedToolSetsToRegistry() {
        AtomicReference<Set<String>> requestedToolSets = new AtomicReference<>();
        AiToolRegistry registry = new AiToolRegistry() {
            @Override
            public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups,
                    boolean includeAllWhenUnspecified) {
                return List.of();
            }

            @Override
            public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups,
                    Set<String> toolSets, boolean includeAllWhenUnspecified) {
                requestedToolSets.set(toolSets);
                return List.of();
            }

            @Override
            public Collection<String> names() {
                return List.of();
            }
        };

        this.contextRunner.withBean(AiToolRegistry.class, () -> registry).run(context -> {
            context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                    .message("query order")
                    .userId("user-a")
                    .conversationId("conversation-a")
                    .addToolSet("order-tools")
                    .build());
            assertThat(requestedToolSets.get()).isEqualTo(Set.of("order-tools"));
        });
    }

    @Test
    void rejectsMysqlSelectionWithoutConnectionConfiguration() {
        this.contextRunner
                .withPropertyValues("fast.ai.persistence.type=mysql")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "MySQL storage requires fast.ai.persistence.mysql.url or a custom fastAiPersistenceDataSource bean");
                });
    }

    @Test
    void rejectsInMemorySqliteConfiguration() {
        this.contextRunner
                .withPropertyValues("fast.ai.persistence.sqlite.file=:memory:")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "In-memory SQLite is not supported; configure a file-backed database");
                });
    }

    private static final class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return response();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(response());
        }

        private ChatResponse response() {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("stub-response"))),
                    ChatResponseMetadata.builder().model("stub-model").usage(new StubUsage()).build());
        }
    }

    private static final class StubUsage implements Usage {

        @Override
        public Integer getPromptTokens() {
            return 42;
        }

        @Override
        public Integer getCompletionTokens() {
            return 7;
        }

        @Override
        public Object getNativeUsage() {
            return null;
        }
    }
}
