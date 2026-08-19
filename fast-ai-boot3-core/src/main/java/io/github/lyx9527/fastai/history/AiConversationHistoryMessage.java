package io.github.lyx9527.fastai.history;

import java.time.Instant;

/**
 * 不受短期上下文窗口裁剪影响的完整对话历史消息。
 *
 * @param id 历史消息唯一标识
 * @param conversationKey 租户、用户和会话作用域生成的脱敏存储 Key
 * @param turnId 所属对话轮次标识
 * @param tenantId 租户标识
 * @param userId 用户标识
 * @param conversationId 业务会话标识
 * @param messageOrder 消息在当前轮次内的顺序，用户消息为 0，助手消息为 1
 * @param messageType 消息类型，当前为 user 或 assistant
 * @param content 消息正文
 * @param createdAt 记录时间
 */
public record AiConversationHistoryMessage(
        String id,
        String conversationKey,
        String turnId,
        String tenantId,
        String userId,
        String conversationId,
        int messageOrder,
        String messageType,
        String content,
        Instant createdAt) {
}
