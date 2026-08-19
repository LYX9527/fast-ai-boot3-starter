package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.chat.AiChatRequest;
import io.github.lyx9527.fastai.chat.AiChatService;
import io.github.lyx9527.fastai.chat.AiStreamEventType;
import io.github.lyx9527.fastai.history.AiConversationHistoryMessage;
import io.github.lyx9527.fastai.history.AiConversationHistoryStore;
import io.github.lyx9527.fastai.history.JdbcAiConversationHistoryStore;
import io.github.lyx9527.fastai.memory.AiConversationKeyFactory;
import io.github.lyx9527.fastai.memory.AiLongTermMemoryStore;
import io.github.lyx9527.fastai.memory.AiMemoryScope;
import io.github.lyx9527.fastai.memory.JdbcAiLongTermMemoryStore;
import io.github.lyx9527.fastai.tool.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
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
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FastAiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastAiAutoConfiguration.class))
            .withBean(ChatModel.class, StubChatModel::new)
            .withBean(ChatClient.Builder.class, () -> ChatClient.builder(new StubChatModel()))
            .withPropertyValues(
                    "fast.ai.memory.long-term.auto-extract=false",
                    "fast.ai.tools.semantic-routing.enabled=false",
                    "fast.ai.persistence.sqlite.file=build/test-fast-ai-" + UUID.randomUUID() + ".db");

    @Test
    void providesInjectableChatService() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AiChatService.class);
            assertThat(context.getBean(ChatMemoryRepository.class)).isInstanceOf(JdbcChatMemoryRepository.class);
            assertThat(context.getBean(AiLongTermMemoryStore.class))
                    .isInstanceOf(JdbcAiLongTermMemoryStore.class);
            assertThat(context.getBean(AiConversationHistoryStore.class))
                    .isInstanceOf(JdbcAiConversationHistoryStore.class);
            AiChatService service = context.getBean(AiChatService.class);
            var response = service.chat(AiChatRequest.builder()
                    .message("hello")
                    .userId("u1")
                    .conversationId("c1")
                    .build());
            assertThat(response.content()).isEqualTo("stub-response");
            assertThat(response.contextUsage().actualPromptTokens()).isEqualTo(42);
            assertThat(response.contextUsage().cumulativePromptTokens()).isEqualTo(42);
            assertThat(response.contextUsage().cumulativeCompletionTokens()).isEqualTo(7);
            assertThat(response.contextUsage().cumulativeTotalTokens()).isEqualTo(49);
            assertThat(response.contextUsage().conversationRequestCount()).isEqualTo(1);
            String memoryKey = context.getBean(AiConversationKeyFactory.class)
                    .create(new AiMemoryScope("default", "u1", "c1"));
            assertThat(context.getBean(ChatMemoryRepository.class).findByConversationId(memoryKey))
                    .extracting(message -> message.getText())
                    .contains("hello", "stub-response");
            assertThat(context.getBean(AiConversationHistoryStore.class)
                    .findByConversation(new AiMemoryScope("default", "u1", "c1")))
                    .extracting(message -> message.messageType() + ":" + message.content())
                    .containsExactly("user:hello", "assistant:stub-response");
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
            assertThat(events.get(0).contextUsage().cumulativeTotalTokens()).isZero();
            assertThat(events.get(1).eventType()).isEqualTo(AiStreamEventType.DELTA);
            assertThat(events.get(1).content()).isEqualTo("stub-response");
            assertThat(events.get(2).eventType()).isEqualTo(AiStreamEventType.COMPLETE);
            assertThat(events.get(2).finalChunk()).isTrue();
            assertThat(events.get(2).contextUsage().actualPromptTokens()).isEqualTo(42);
            assertThat(events.get(2).contextUsage().cumulativePromptTokens()).isEqualTo(42);
            assertThat(events.get(2).contextUsage().cumulativeCompletionTokens()).isEqualTo(7);
            assertThat(events.get(2).contextUsage().cumulativeTotalTokens()).isEqualTo(49);
            assertThat(events.get(2).contextUsage().conversationRequestCount()).isEqualTo(1);
            String memoryKey = context.getBean(AiConversationKeyFactory.class)
                    .create(new AiMemoryScope("default", "u1", "stream-c1"));
            assertThat(context.getBean(ChatMemoryRepository.class).findByConversationId(memoryKey))
                    .extracting(message -> message.getText())
                    .containsExactly("hello", "stub-response");

            var secondEvents = service.stream(AiChatRequest.builder()
                            .message("hello again")
                            .userId("u1")
                            .conversationId("stream-c1")
                            .build())
                    .collectList()
                    .block();
            assertThat(secondEvents).isNotNull().hasSize(3);
            assertThat(secondEvents.get(0).contextUsage().cumulativeTotalTokens()).isEqualTo(49);
            assertThat(secondEvents.get(0).contextUsage().conversationRequestCount()).isEqualTo(1);
            assertThat(secondEvents.get(2).contextUsage().cumulativePromptTokens()).isEqualTo(84);
            assertThat(secondEvents.get(2).contextUsage().cumulativeCompletionTokens()).isEqualTo(14);
            assertThat(secondEvents.get(2).contextUsage().cumulativeTotalTokens()).isEqualTo(98);
            assertThat(secondEvents.get(2).contextUsage().conversationRequestCount()).isEqualTo(2);
            assertThat(context.getBean(AiConversationHistoryStore.class)
                    .findByConversation(new AiMemoryScope("default", "u1", "stream-c1")))
                    .extracting(message -> message.messageType() + ":" + message.content())
                    .containsExactly("user:hello", "assistant:stub-response",
                            "user:hello again", "assistant:stub-response");
        });
    }

    @Test
    void offloadsStreamingPreparationAndPersistenceFromReactorEventThreads() {
        AtomicBoolean routingRanOnNonBlockingThread = new AtomicBoolean();
        AtomicBoolean historyRanOnNonBlockingThread = new AtomicBoolean();
        AiToolSelectionService routingService = request -> {
            routingRanOnNonBlockingThread.set(Schedulers.isInNonBlockingThread());
            return AiToolSelectionResult.none();
        };
        AiConversationHistoryStore historyStore = new AiConversationHistoryStore() {
            @Override
            public void appendTurn(AiMemoryScope scope, String conversationKey,
                    String userMessage, String assistantMessage) {
                historyRanOnNonBlockingThread.set(Schedulers.isInNonBlockingThread());
            }

            @Override
            public List<AiConversationHistoryMessage> findByConversation(AiMemoryScope scope) {
                return List.of();
            }

            @Override
            public void clear(AiMemoryScope scope) {
            }
        };
        this.contextRunner
                .withPropertyValues("fast.ai.tools.semantic-routing.enabled=true")
                .withBean(AiToolSelectionService.class, () -> routingService)
                .withBean("fastAiConversationHistoryStore", AiConversationHistoryStore.class, () -> historyStore)
                .run(context -> {
                    var events = context.getBean(AiChatService.class)
                            .stream(AiChatRequest.builder()
                                    .message("查询订单")
                                    .userId("event-loop-user")
                                    .conversationId("event-loop-conversation")
                                    .build())
                            .subscribeOn(Schedulers.parallel())
                            .collectList()
                            .block();

                    assertThat(events).isNotNull().hasSize(3);
                    assertThat(routingRanOnNonBlockingThread).isFalse();
                    assertThat(historyRanOnNonBlockingThread).isFalse();
                });
    }

    @Test
    void completeHistoryRemainsPersistentWhenShortTermMemoryIsDisabled() {
        this.contextRunner
                .withPropertyValues("fast.ai.memory.short-term.enabled=false")
                .run(context -> {
                    AiMemoryScope scope = new AiMemoryScope("default", "history-user", "history-conversation");
                    String conversationKey = context.getBean(AiConversationKeyFactory.class).create(scope);
                    AiChatService service = context.getBean(AiChatService.class);

                    service.chat(AiChatRequest.builder()
                            .message("需要永久保存的消息")
                            .userId(scope.userId())
                            .conversationId(scope.conversationId())
                            .build());

                    assertThat(context.getBean(ChatMemoryRepository.class)
                            .findByConversationId(conversationKey)).isEmpty();
                    assertThat(context.getBean(AiConversationHistoryStore.class).findByConversation(scope))
                            .extracting(message -> message.messageType() + ":" + message.content())
                            .containsExactly("user:需要永久保存的消息", "assistant:stub-response");

                    service.clearConversation(scope.tenantId(), scope.userId(), scope.conversationId());
                    assertThat(context.getBean(AiConversationHistoryStore.class).findByConversation(scope)).isEmpty();
                });
    }

    @Test
    void defaultMemoryExtractionDoesNotInvokeChatModelAgain() {
        CountingChatModel model = new CountingChatModel();
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FastAiAutoConfiguration.class))
                .withBean(ChatModel.class, () -> model)
                .withBean(ChatClient.Builder.class, () -> ChatClient.builder(model))
                .withPropertyValues(
                        "fast.ai.memory.long-term.auto-extract=true",
                        "fast.ai.tools.semantic-routing.enabled=false",
                        "fast.ai.persistence.sqlite.file=build/test-fast-ai-memory-"
                                + UUID.randomUUID() + ".db");

        runner.run(context -> {
            AiMemoryScope scope = new AiMemoryScope("default", "memory-user", "memory-conversation");
            context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                    .message("我喜欢无糖咖啡")
                    .userId(scope.userId())
                    .conversationId(scope.conversationId())
                    .build());

            List<String> memories = waitForMemories(context.getBean(AiLongTermMemoryStore.class), scope);
            assertThat(memories).containsExactly("我喜欢无糖咖啡");
            assertThat(model.calls).hasValue(1);
        });
    }

    @Test
    void prefersStarterJdbcMemoryWhenSpringAiMemoryBeansAlreadyExist() {
        InMemoryChatMemoryRepository inMemoryRepository = new InMemoryChatMemoryRepository();
        this.contextRunner
                .withBean("springAiChatMemoryRepository", ChatMemoryRepository.class, () -> inMemoryRepository)
                .withBean("springAiChatMemory", org.springframework.ai.chat.memory.ChatMemory.class,
                        () -> MessageWindowChatMemory.builder()
                                .chatMemoryRepository(inMemoryRepository)
                                .build())
                .run(context -> {
                    ChatMemoryRepository repository = context.getBean(ChatMemoryRepository.class);
                    assertThat(repository).isInstanceOf(JdbcChatMemoryRepository.class);
                    assertThat(context.getBean("fastAiChatMemoryRepository")).isSameAs(repository);

                    context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                            .message("persistent hello")
                            .userId("u-persistent")
                            .conversationId("c-persistent")
                            .build());

                    String memoryKey = context.getBean(AiConversationKeyFactory.class)
                            .create(new AiMemoryScope("default", "u-persistent", "c-persistent"));
                    assertThat(repository.findByConversationId(memoryKey))
                            .extracting(message -> message.getText())
                            .containsExactly("persistent hello", "stub-response");
                    assertThat(inMemoryRepository.findByConversationId(memoryKey)).isEmpty();
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
                .confirmTool("order-delete")
                .metadata(Map.of(
                        AiToolContextValues.TENANT_ID, "attacker-tenant",
                        AiToolContextValues.PERMISSIONS, Set.of("admin:*"),
                        AiToolContextValues.CONFIRMED_TOOLS, Set.of("*")))
                .build();

        Map<String, Object> context = DefaultAiChatService.toolContext(
                new AiMemoryScope("tenant-a", "user-a", "conversation-a"), request);

        assertThat(context.get(AiToolContextValues.TENANT_ID)).isEqualTo("tenant-a");
        assertThat(context.get(AiToolContextValues.PERMISSIONS)).isEqualTo(Set.of("order:delete"));
        assertThat(context.get(AiToolContextValues.CONFIRMED_TOOLS)).isEqualTo(Set.of("order-delete"));
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
    void explicitToolSelectionTakesPriorityOverIntentRouting() {
        RecordingToolRegistry registry = new RecordingToolRegistry();
        AtomicInteger routingCalls = new AtomicInteger();
        AiToolSelectionService routingService = request -> {
            routingCalls.incrementAndGet();
            return new AiToolSelectionResult(Set.of("routed-order-tool"), 0.99);
        };

        this.contextRunner
                .withPropertyValues("fast.ai.tools.semantic-routing.enabled=true")
                .withBean(AiToolRegistry.class, () -> registry)
                .withBean(AiToolSelectionService.class, () -> routingService)
                .run(context -> {
                    context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                            .message("查询订单")
                            .userId("user-a")
                            .conversationId("conversation-a")
                            .addToolGroup("explicit-group")
                            .build());

                    assertThat(routingCalls).hasValue(0);
                    assertThat(registry.toolGroups).isEqualTo(Set.of("explicit-group"));
                    assertThat(registry.includeAllWhenUnspecified).isFalse();
                });
    }

    @Test
    void routesOnlyLlmSelectedToolWhenRequestDoesNotSelectTools() {
        RecordingToolRegistry registry = new RecordingToolRegistry();
        AiToolSelectionService routingService = request ->
                new AiToolSelectionResult(Set.of("demo-order-query"), 0.96);

        this.contextRunner
                .withPropertyValues("fast.ai.tools.semantic-routing.enabled=true")
                .withBean(AiToolRegistry.class, () -> registry)
                .withBean(AiToolSelectionService.class, () -> routingService)
                .run(context -> {
                    context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                            .message("订单 A1002 到哪里了")
                            .userId("user-a")
                            .conversationId("conversation-a")
                            .build());

                    assertThat(registry.resolveCalls).isEqualTo(1);
                    assertThat(registry.toolNames).isEqualTo(Set.of("demo-order-query"));
                    assertThat(registry.toolGroups).isEmpty();
                    assertThat(registry.toolSets).isEmpty();
                    assertThat(registry.includeAllWhenUnspecified).isFalse();
                });
    }

    @Test
    void emptySemanticSelectionNeverFallsBackToAllTools() {
        RecordingToolRegistry registry = new RecordingToolRegistry();
        AiToolSelectionService routingService = request -> AiToolSelectionResult.none();

        this.contextRunner
                .withPropertyValues("fast.ai.tools.include-all-when-unspecified=true",
                        "fast.ai.tools.semantic-routing.enabled=true")
                .withBean(AiToolRegistry.class, () -> registry)
                .withBean(AiToolSelectionService.class, () -> routingService)
                .run(context -> {
                    context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                            .message("普通闲聊")
                            .userId("user-a")
                            .conversationId("conversation-a")
                            .build());

                    assertThat(registry.resolveCalls).isZero();
                });
    }

    @Test
    void requestCanDisableBothExplicitAndSemanticRoutedTools() {
        RecordingToolRegistry registry = new RecordingToolRegistry();
        AtomicInteger routingCalls = new AtomicInteger();
        AiToolSelectionService routingService = request -> {
            routingCalls.incrementAndGet();
            return new AiToolSelectionResult(Set.of("demo-order-query"), 0.99);
        };

        this.contextRunner
                .withPropertyValues("fast.ai.tools.semantic-routing.enabled=true")
                .withBean(AiToolRegistry.class, () -> registry)
                .withBean(AiToolSelectionService.class, () -> routingService)
                .run(context -> {
                    context.getBean(AiChatService.class).chat(AiChatRequest.builder()
                            .message("查询订单")
                            .userId("user-a")
                            .conversationId("conversation-a")
                            .addToolGroup("explicit-group")
                            .toolsEnabled(false)
                            .build());

                    assertThat(routingCalls).hasValue(0);
                    assertThat(registry.resolveCalls).isZero();
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
            return response("stub-response");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(response(null), response("stub-response"))
                    .publishOn(Schedulers.parallel());
        }

        private ChatResponse response(String content) {
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(content).build())),
                    ChatResponseMetadata.builder().model("stub-model").usage(new StubUsage()).build());
        }
    }

    /** 记录同步模型调用次数的长期记忆回归测试模型。 */
    private static final class CountingChatModel implements ChatModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            this.calls.incrementAndGet();
            return new StubChatModel().response("stub-response");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.calls.incrementAndGet();
            return Flux.just(new StubChatModel().response("stub-response"));
        }
    }

    private static List<String> waitForMemories(AiLongTermMemoryStore store, AiMemoryScope scope) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            List<String> memories = store.search(scope, "咖啡", 5).stream()
                    .map(memory -> memory.content())
                    .toList();
            if (!memories.isEmpty()) {
                return memories;
            }
            try {
                Thread.sleep(20);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待长期记忆写入时线程被中断", exception);
            }
        }
        return List.of();
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

    /** 记录每次 Tool 解析条件的测试注册表。 */
    private static final class RecordingToolRegistry implements AiToolRegistry {

        private Set<String> toolNames = Set.of();
        private Set<String> toolGroups = Set.of();
        private Set<String> toolSets = Set.of();
        private boolean includeAllWhenUnspecified;
        private int resolveCalls;

        @Override
        public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups,
                boolean includeAllWhenUnspecified) {
            return resolve(toolNames, toolGroups, Set.of(), includeAllWhenUnspecified);
        }

        @Override
        public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups,
                Set<String> toolSets, boolean includeAllWhenUnspecified) {
            this.toolNames = Set.copyOf(toolNames);
            this.toolGroups = Set.copyOf(toolGroups);
            this.toolSets = Set.copyOf(toolSets);
            this.includeAllWhenUnspecified = includeAllWhenUnspecified;
            this.resolveCalls++;
            return List.of();
        }

        @Override
        public Collection<String> names() {
            return List.of();
        }
    }
}
