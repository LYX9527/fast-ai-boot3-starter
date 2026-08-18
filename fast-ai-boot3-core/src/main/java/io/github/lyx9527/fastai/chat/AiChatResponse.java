package io.github.lyx9527.fastai.chat;

import io.github.lyx9527.fastai.context.AiContextUsage;

import java.util.Map;

/**
 * 同步对话的统一响应。
 *
 * @param content 模型回复文本
 * @param conversationId 会话标识
 * @param provider 实际 Provider 类型
 * @param model 实际模型名称
 * @param promptTokens Provider 返回的输入 Token 数
 * @param completionTokens Provider 返回的输出 Token 数
 * @param totalTokens Provider 返回的总 Token 数
 * @param metadata Provider 响应扩展元数据
 * @param contextUsage 上下文窗口占用信息
 */
public record AiChatResponse(
        String content,
        String conversationId,
        String provider,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Map<String, Object> metadata,
        AiContextUsage contextUsage) {

    public AiChatResponse {
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public AiChatResponse(String content, String conversationId, String provider, String model,
            Integer promptTokens, Integer completionTokens, Integer totalTokens, Map<String, Object> metadata) {
        this(content, conversationId, provider, model, promptTokens, completionTokens, totalTokens, metadata, null);
    }
}
