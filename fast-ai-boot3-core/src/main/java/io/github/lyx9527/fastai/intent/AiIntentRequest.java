package io.github.lyx9527.fastai.intent;

import io.github.lyx9527.fastai.memory.AiMemoryScope;

/**
 * 意图识别请求。
 *
 * @param message 待识别的用户消息
 * @param scope 当前租户、用户和会话作用域
 */
public record AiIntentRequest(String message, AiMemoryScope scope) {

    public AiIntentRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
    }
}
