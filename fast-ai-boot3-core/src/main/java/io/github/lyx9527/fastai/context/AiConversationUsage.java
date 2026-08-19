package io.github.lyx9527.fastai.context;

/**
 * 单个用户会话累计消耗的 Token 和成功请求次数。
 *
 * @param cumulativePromptTokens 累计输入 Token 数
 * @param cumulativeCompletionTokens 累计输出 Token 数
 * @param cumulativeTotalTokens 累计总 Token 数
 * @param requestCount 成功完成的模型请求次数
 */
public record AiConversationUsage(
        long cumulativePromptTokens,
        long cumulativeCompletionTokens,
        long cumulativeTotalTokens,
        long requestCount) {

    public AiConversationUsage {
        cumulativePromptTokens = Math.max(0, cumulativePromptTokens);
        cumulativeCompletionTokens = Math.max(0, cumulativeCompletionTokens);
        cumulativeTotalTokens = Math.max(0, cumulativeTotalTokens);
        requestCount = Math.max(0, requestCount);
    }

    public static AiConversationUsage empty() {
        return new AiConversationUsage(0, 0, 0, 0);
    }
}
