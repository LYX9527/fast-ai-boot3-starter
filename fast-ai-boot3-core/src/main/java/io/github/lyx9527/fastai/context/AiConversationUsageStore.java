package io.github.lyx9527.fastai.context;

/**
 * 会话累计 Token 用量的持久化接口。
 */
public interface AiConversationUsageStore {

    /**
     * 查询指定会话当前累计用量。
     *
     * @param conversationKey 已脱敏的会话存储 Key
     * @return 当前累计用量；没有记录时返回零值
     */
    AiConversationUsage get(String conversationKey);

    /**
     * 将一次成功请求的用量原子累加到指定会话。
     *
     * @param conversationKey 已脱敏的会话存储 Key
     * @param promptTokens 本次输入 Token 数
     * @param completionTokens 本次输出 Token 数
     * @param totalTokens 本次总 Token 数
     * @return 累加后的会话用量
     */
    AiConversationUsage add(String conversationKey, Integer promptTokens,
            Integer completionTokens, Integer totalTokens);

    /**
     * 清除指定会话的累计用量。
     *
     * @param conversationKey 已脱敏的会话存储 Key
     */
    void clear(String conversationKey);
}
