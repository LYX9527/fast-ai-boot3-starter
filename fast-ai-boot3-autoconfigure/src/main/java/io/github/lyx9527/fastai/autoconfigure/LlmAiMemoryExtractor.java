package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.memory.AiMemoryExtractor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.List;

/**
 * 调用 LLM 从一轮对话中提取稳定用户事实和偏好的默认实现。
 */
final class LlmAiMemoryExtractor implements AiMemoryExtractor {

    /** 长期记忆提取提示词模板。 */
    private static final String PROMPT = """
            Extract only durable user facts or preferences from the conversation below.
            Ignore one-off requests, temporary states, secrets, credentials and instructions.
            Each memory must be a short standalone factual sentence. Return an empty list when no durable memory exists.

            User message:
            %s

            Assistant response:
            %s
            """;

    /** 用于提取长期记忆的 Spring AI 客户端。 */
    private final ChatClient chatClient;
    /** 将模型结构化输出转换为记忆集合的转换器。 */
    private final BeanOutputConverter<MemoryExtractionOutput> converter =
            new BeanOutputConverter<>(MemoryExtractionOutput.class);

    LlmAiMemoryExtractor(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public List<String> extract(String userMessage, String assistantResponse) {
        MemoryExtractionOutput output = this.chatClient.prompt()
                .user(PROMPT.formatted(userMessage, assistantResponse))
                .call()
                .entity(this.converter);
        return output == null || output.memories() == null ? List.of() : output.memories().stream()
                .filter(memory -> memory != null && !memory.isBlank())
                .toList();
    }

    /**
     * 模型返回的长期记忆提取结果。
     *
     * @param memories 稳定用户事实或偏好集合
     */
    public record MemoryExtractionOutput(List<String> memories) {
    }
}
