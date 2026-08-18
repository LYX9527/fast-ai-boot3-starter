package io.github.lyx9527.fastai.memory;

/**
 * AI 记忆的租户、用户和会话隔离作用域。
 *
 * @param tenantId 租户标识
 * @param userId 用户标识
 * @param conversationId 会话标识
 */
public record AiMemoryScope(String tenantId, String userId, String conversationId) {

    public AiMemoryScope {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
    }
}
