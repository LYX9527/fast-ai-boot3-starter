package io.github.lyx9527.fastai.chat;

import reactor.core.publisher.Flux;

/**
 * 业务系统统一注入使用的 AI 对话服务。
 */
public interface AiChatService {

    /**
     * 执行一次同步对话。
     *
     * @param request 对话请求
     * @return 统一对话响应
     */
    AiChatResponse chat(AiChatRequest request);

    /**
     * 执行一次流式对话。
     *
     * @param request 对话请求
     * @return 上下文、文本增量和完成事件流
     */
    Flux<AiChatChunk> stream(AiChatRequest request);

    /**
     * 清理指定作用域的短期上下文、完整对话历史和累计 Token 数据。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param conversationId 会话标识
     */
    void clearConversation(String tenantId, String userId, String conversationId);
}
