package io.github.lyx9527.fastai.context;

/**
 * 同步和流式调用统一使用的上下文窗口占用信息。
 *
 * @param estimatedPromptTokens 请求前估算的 Prompt Token 数
 * @param actualPromptTokens Provider 返回的实际 Prompt Token 数
 * @param maxContextTokens 模型最大上下文 Token 数
 * @param occupancyRate 当前上下文占用率
 * @param compressed 本轮请求前是否执行了压缩
 * @param tokensBeforeCompression 压缩前估算 Token 数
 * @param messagesBeforeCompression 压缩前历史消息数
 * @param messagesAfterCompression 压缩后历史消息数
 * @param summarizedMessages 被摘要的历史消息数
 */
public record AiContextUsage(
        int estimatedPromptTokens,
        Integer actualPromptTokens,
        int maxContextTokens,
        double occupancyRate,
        boolean compressed,
        int tokensBeforeCompression,
        int messagesBeforeCompression,
        int messagesAfterCompression,
        int summarizedMessages) {

    public AiContextUsage {
        estimatedPromptTokens = Math.max(0, estimatedPromptTokens);
        maxContextTokens = Math.max(1, maxContextTokens);
        occupancyRate = clamp(occupancyRate);
        tokensBeforeCompression = Math.max(0, tokensBeforeCompression);
        messagesBeforeCompression = Math.max(0, messagesBeforeCompression);
        messagesAfterCompression = Math.max(0, messagesAfterCompression);
        summarizedMessages = Math.max(0, summarizedMessages);
    }

    public static AiContextUsage estimated(int estimatedPromptTokens, int maxContextTokens,
            boolean compressed, int tokensBeforeCompression, int messagesBeforeCompression,
            int messagesAfterCompression, int summarizedMessages) {
        return new AiContextUsage(estimatedPromptTokens, null, maxContextTokens,
                ratio(estimatedPromptTokens, maxContextTokens), compressed, tokensBeforeCompression,
                messagesBeforeCompression, messagesAfterCompression, summarizedMessages);
    }

    public AiContextUsage withActualPromptTokens(Integer promptTokens) {
        if (promptTokens == null || promptTokens <= 0) {
            return this;
        }
        return new AiContextUsage(this.estimatedPromptTokens, promptTokens, this.maxContextTokens,
                ratio(promptTokens, this.maxContextTokens), this.compressed, this.tokensBeforeCompression,
                this.messagesBeforeCompression, this.messagesAfterCompression, this.summarizedMessages);
    }

    public int occupiedTokens() {
        return this.actualPromptTokens != null && this.actualPromptTokens > 0
                ? this.actualPromptTokens : this.estimatedPromptTokens;
    }

    private static double ratio(int tokens, int maxTokens) {
        return clamp((double) Math.max(0, tokens) / Math.max(1, maxTokens));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
