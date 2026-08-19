package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.memory.AiConversationKeyFactory;
import io.github.lyx9527.fastai.memory.AiMemoryScope;
import io.github.lyx9527.fastai.memory.Sha256ConversationKeyFactory;
import io.github.lyx9527.fastai.tool.AiToolMetadata;
import io.github.lyx9527.fastai.tool.AiToolRegistry;
import io.github.lyx9527.fastai.tool.AiToolSelectionRequest;
import io.github.lyx9527.fastai.tool.AiToolSelectionResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 默认 LLM Tool 语义选择服务回归测试。 */
class DefaultAiToolSelectionServiceTest {

    @Test
    void selectsWeatherToolFromGeneratedMetadataWithoutIntentDefinitions() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = responseModel(calls, """
                {"toolNames":["demo-weather-query"],"confidence":0.94}
                """);
        AiToolSelectionResult result = service(model).select(request(
                "帮我查一下国内热门城市的天气，推荐下我去哪里玩"));

        assertThat(calls).hasValue(1);
        assertThat(result.toolNames()).containsExactly("demo-weather-query");
        assertThat(result.confidence()).isEqualTo(0.94);
    }

    @Test
    void keepsNormalConversationWithoutTools() {
        ChatModel model = responseModel(new AtomicInteger(), """
                {"toolNames":[],"confidence":0.98}
                """);

        AiToolSelectionResult result = service(model).select(request("介绍一下你自己"));

        assertThat(result.hasSelection()).isFalse();
    }

    @Test
    void ignoresUnknownNamesAndRejectsLowConfidenceSelection() {
        ChatModel unknownModel = responseModel(new AtomicInteger(), """
                {"toolNames":["invented-tool"],"confidence":0.99}
                """);
        assertThat(service(unknownModel).select(request("查询未知业务")).hasSelection()).isFalse();

        ChatModel lowConfidenceModel = responseModel(new AtomicInteger(), """
                {"toolNames":["demo-order-query"],"confidence":0.45}
                """);
        assertThat(service(lowConfidenceModel).select(request("可能想看看订单")).hasSelection()).isFalse();
    }

    @Test
    void toleratesMarkdownAndNonStandardJsonFields() {
        ChatModel model = responseModel(new AtomicInteger(), """
                路由结果：
                ```json
                {"tool_names":"DEMO-WEATHER-QUERY","score":"91%"}
                ```
                """);

        AiToolSelectionResult result = service(model).select(request("今天出门要不要带伞"));

        assertThat(result.toolNames()).containsExactly("demo-weather-query");
        assertThat(result.confidence()).isEqualTo(0.91);
    }

    @Test
    void batchesLargeCatalogSoOneRoutingPromptStaysBounded() {
        List<AiToolMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            metadata.add(new AiToolMetadata("business-tool-" + index, "处理业务能力 " + index,
                    Set.of("business"), "enterprise-tools", "企业工具集"));
        }
        metadata.add(new AiToolMetadata("weather-tool", "查询城市实时天气",
                Set.of("weather"), "enterprise-tools", "企业工具集"));
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger maxCatalogSize = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                calls.incrementAndGet();
                String content = prompt.getContents();
                int catalogSize = content.split("\\n- name: ", -1).length - 1;
                maxCatalogSize.updateAndGet(current -> Math.max(current, catalogSize));
                String output = content.contains("name: weather-tool")
                        ? "{\"toolNames\":[\"weather-tool\"],\"confidence\":0.95}"
                        : "{\"toolNames\":[],\"confidence\":0.95}";
                return new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder().content(output).build())));
            }
        };
        AiToolRegistry largeRegistry = registry(metadata);
        AiConversationKeyFactory keyFactory = new Sha256ConversationKeyFactory();
        DefaultAiToolSelectionService selectionService = new DefaultAiToolSelectionService(
                ChatClient.builder(model).build(), largeRegistry,
                MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(20)
                        .build(), keyFactory, 0.7, 5, 4, 0);

        AiToolSelectionResult result = selectionService.select(request("帮我查询上海天气"));

        assertThat(result.toolNames()).containsExactly("weather-tool");
        assertThat(calls).hasValue(4);
        assertThat(maxCatalogSize).hasValue(4);
    }

    private DefaultAiToolSelectionService service(ChatModel model) {
        AiConversationKeyFactory keyFactory = new Sha256ConversationKeyFactory();
        return new DefaultAiToolSelectionService(ChatClient.builder(model).build(), registry(),
                MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(20)
                        .build(),
                keyFactory, 0.7, 5, 24, 6);
    }

    private AiToolRegistry registry() {
        return registry(List.of(
                new AiToolMetadata("demo-order-query", "根据订单号查询订单状态和物流进度",
                        Set.of("order-query"), "demo-support", "企业客服演示工具集"),
                new AiToolMetadata("demo-weather-query", "查询指定城市的实时天气信息",
                        Set.of("weather-query"), "demo-support", "企业客服演示工具集")));
    }

    private AiToolRegistry registry(List<AiToolMetadata> metadata) {
        return new AiToolRegistry() {
            @Override
            public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups,
                    boolean includeAllWhenUnspecified) {
                return List.of();
            }

            @Override
            public Collection<String> names() {
                return metadata.stream().map(AiToolMetadata::name).toList();
            }

            @Override
            public Collection<AiToolMetadata> metadata() {
                return metadata;
            }
        };
    }

    private ChatModel responseModel(AtomicInteger calls, String content) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                calls.incrementAndGet();
                return new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder().content(content).build())));
            }
        };
    }

    private AiToolSelectionRequest request(String message) {
        return new AiToolSelectionRequest(message,
                new AiMemoryScope("tenant", "user", "conversation"));
    }
}
