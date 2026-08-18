package io.github.lyx9527.fastai.memory;

import java.time.Instant;
import java.util.Map;

/**
 * 一条用户长期记忆。
 *
 * @param id 记忆唯一标识
 * @param tenantId 租户标识
 * @param userId 用户标识
 * @param content 稳定事实或偏好内容
 * @param memoryType 记忆类型
 * @param sourceConversationId 来源会话标识
 * @param createdAt 创建时间
 * @param expiresAt 可选过期时间
 * @param metadata 扩展元数据
 */
public record AiMemoryItem(
        String id,
        String tenantId,
        String userId,
        String content,
        String memoryType,
        String sourceConversationId,
        Instant createdAt,
        Instant expiresAt,
        Map<String, Object> metadata) {

    public AiMemoryItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        memoryType = memoryType == null || memoryType.isBlank() ? "fact" : memoryType;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean expired(Instant now) {
        return this.expiresAt != null && !this.expiresAt.isAfter(now);
    }
}
