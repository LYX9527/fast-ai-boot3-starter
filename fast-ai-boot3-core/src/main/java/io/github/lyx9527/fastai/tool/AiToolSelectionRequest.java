package io.github.lyx9527.fastai.tool;

import io.github.lyx9527.fastai.memory.AiMemoryScope;

/**
 * LLM Tool 语义选择请求。
 *
 * @param message 当前用户消息
 * @param scope 当前租户、用户和会话作用域
 */
public record AiToolSelectionRequest(String message, AiMemoryScope scope) {

    public AiToolSelectionRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
    }
}
