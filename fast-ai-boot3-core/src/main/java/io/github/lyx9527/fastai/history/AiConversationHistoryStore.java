package io.github.lyx9527.fastai.history;

import io.github.lyx9527.fastai.memory.AiMemoryScope;

import java.util.List;

/**
 * 完整对话历史的持久化和查询接口。
 */
public interface AiConversationHistoryStore {

    /**
     * 将一轮成功对话追加到历史表。
     *
     * @param scope 租户、用户和会话作用域
     * @param conversationKey 作用域生成的脱敏存储 Key
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    void appendTurn(AiMemoryScope scope, String conversationKey, String userMessage, String assistantMessage);

    /**
     * 查询指定租户、用户和会话的完整历史。
     *
     * @param scope 租户、用户和会话作用域
     * @return 按轮次和轮次内消息顺序排列的完整历史
     */
    List<AiConversationHistoryMessage> findByConversation(AiMemoryScope scope);

    /**
     * 清除指定会话的完整历史。
     *
     * @param scope 租户、用户和会话作用域
     */
    void clear(AiMemoryScope scope);
}
