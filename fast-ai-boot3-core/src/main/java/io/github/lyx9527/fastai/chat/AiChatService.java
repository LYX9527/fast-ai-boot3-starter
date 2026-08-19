package io.github.lyx9527.fastai.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
     * 创建适用于 Spring MVC Controller 的 SSE 输出器，默认连接超时为 5 分钟。
     * <p>
     * 事件名依次为 {@code context}、{@code delta} 和 {@code complete}，事件数据为
     * {@link AiChatChunk}。连接完成、超时或客户端断开时会自动取消底层流式订阅。
     *
     * @param request 对话请求
     * @return 已订阅当前流式对话的 SSE 输出器
     */
    default SseEmitter streamSse(AiChatRequest request) {
        return AiChatSseEmitterAdapter.create(this, request);
    }

    /**
     * 创建带指定超时时间的 Spring MVC SSE 输出器。
     *
     * @param request 对话请求
     * @param timeoutMillis SSE 连接超时毫秒数；小于或等于 0 表示不单独设置超时
     * @return 已订阅当前流式对话的 SSE 输出器
     */
    default SseEmitter streamSse(AiChatRequest request, long timeoutMillis) {
        return AiChatSseEmitterAdapter.create(this, request, timeoutMillis);
    }

    /**
     * 清理指定作用域的短期上下文、完整对话历史和累计 Token 数据。
     *
     * @param tenantId 租户标识
     * @param userId 用户标识
     * @param conversationId 会话标识
     */
    void clearConversation(String tenantId, String userId, String conversationId);
}
