package io.github.lyx9527.fastai.context;

import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;
import java.util.List;

/**
 * 负责计算上下文窗口占用，并在达到阈值时压缩持久化会话历史。
 */
public interface AiContextManager {

    /**
     * 计算不包含 Tool Schema 时的上下文占用，并按需压缩会话历史。
     *
     * @param conversationKey 会话持久化 Key
     * @param systemPrompt 系统提示词
     * @param userMessage 当前用户消息
     * @return 上下文占用信息
     */
    default AiContextUsage prepare(String conversationKey, String systemPrompt, String userMessage) {
        return prepare(conversationKey, systemPrompt, userMessage, List.of());
    }

    /**
     * 计算包含 Tool Schema 时的上下文占用，并按需压缩会话历史。
     *
     * @param conversationKey 会话持久化 Key
     * @param systemPrompt 系统提示词
     * @param userMessage 当前用户消息
     * @param toolCallbacks 本次请求注入的 Tool
     * @return 上下文占用信息
     */
    AiContextUsage prepare(String conversationKey, String systemPrompt, String userMessage,
            Collection<ToolCallback> toolCallbacks);
}
