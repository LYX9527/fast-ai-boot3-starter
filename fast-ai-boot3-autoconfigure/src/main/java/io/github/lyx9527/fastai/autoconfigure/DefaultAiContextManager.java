package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.context.AiContextCompressor;
import io.github.lyx9527.fastai.context.AiContextManager;
import io.github.lyx9527.fastai.context.AiContextUsage;
import io.github.lyx9527.fastai.context.AiTokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 默认长上下文管理器，负责估算 Token、分批摘要并回写持久化会话窗口。
 */
final class DefaultAiContextManager implements AiContextManager {

    /** 写入会话历史时用于识别压缩摘要的固定前缀。 */
    static final String SUMMARY_PREFIX = "[FAST_AI_COMPRESSED_CONTEXT]";

    /** 上下文压缩异常日志记录器。 */
    private static final Logger logger = LoggerFactory.getLogger(DefaultAiContextManager.class);

    /** 持久化短期会话窗口。 */
    private final ChatMemory chatMemory;
    /** Provider 无关的 Token 估算器。 */
    private final AiTokenEstimator tokenEstimator;
    /** 使用模型生成摘要的上下文压缩器。 */
    private final AiContextCompressor compressor;
    /** 上下文阈值和批次配置。 */
    private final FastAiProperties properties;

    DefaultAiContextManager(ChatMemory chatMemory, AiTokenEstimator tokenEstimator,
            AiContextCompressor compressor, FastAiProperties properties) {
        this.chatMemory = chatMemory;
        this.tokenEstimator = tokenEstimator;
        this.compressor = compressor;
        this.properties = properties;
    }

    @Override
    public AiContextUsage prepare(String conversationKey, String systemPrompt, String userMessage,
            Collection<ToolCallback> toolCallbacks) {
        List<Message> history = new ArrayList<>(this.chatMemory.get(conversationKey));
        FastAiProperties.Context context = this.properties.getContext();
        int maxContextTokens = Math.max(1_024, context.getMaxContextTokens());
        int reservedOutputTokens = Math.max(0,
                Math.min(context.getReservedOutputTokens(), maxContextTokens - 1));
        int promptBudget = maxContextTokens - reservedOutputTokens;
        int tokensBefore = estimatePrompt(systemPrompt, userMessage, history, toolCallbacks);
        int triggerTokens = (int) Math.floor(promptBudget * clamp(context.getCompressionThreshold(), 0.1, 1));

        if (!context.isCompressionEnabled() || tokensBefore <= triggerTokens || history.isEmpty()) {
            return usage(tokensBefore, maxContextTokens, false, tokensBefore, history.size(), history.size(), 0);
        }

        int preserveCount = Math.min(Math.max(0, context.getPreserveRecentMessages()), history.size());
        int splitIndex = history.size() - preserveCount;
        if (splitIndex <= 0) {
            return usage(tokensBefore, maxContextTokens, false, tokensBefore, history.size(), history.size(), 0);
        }

        List<Message> messagesToSummarize = new ArrayList<>(history.subList(0, splitIndex));
        List<Message> recentMessages = new ArrayList<>(history.subList(splitIndex, history.size()));
        int targetTokens = (int) Math.floor(promptBudget * clamp(context.getTargetOccupancy(), 0.1, 0.95));
        int fixedTokens = estimatePrompt(systemPrompt, userMessage, recentMessages, toolCallbacks);
        int effectiveSummaryTokens = Math.max(128,
                Math.min(context.getSummaryMaxTokens(), Math.max(128, targetTokens - fixedTokens)));

        try {
            String summary = summarizeInBatches(messagesToSummarize, effectiveSummaryTokens,
                    Math.max(512, Math.min(context.getCompressionBatchTokens(), promptBudget / 2)));
            if (summary.isBlank()) {
                return usage(tokensBefore, maxContextTokens, false, tokensBefore, history.size(), history.size(), 0);
            }
            List<Message> compressedHistory = new ArrayList<>();
            // 压缩摘要使用助手消息保存，确保下一次请求中业务系统提示词仍位于最前方。
            compressedHistory.add(new AssistantMessage(SUMMARY_PREFIX + "\n" + summary));
            compressedHistory.addAll(recentMessages);
            this.chatMemory.clear(conversationKey);
            this.chatMemory.add(conversationKey, compressedHistory);
            int tokensAfter = estimatePrompt(systemPrompt, userMessage, compressedHistory, toolCallbacks);
            return usage(tokensAfter, maxContextTokens, true, tokensBefore, history.size(),
                    compressedHistory.size(), messagesToSummarize.size());
        }
        catch (RuntimeException exception) {
            logger.warn("Context compression failed for conversation memory key {}", conversationKey, exception);
            return usage(tokensBefore, maxContextTokens, false, tokensBefore, history.size(), history.size(), 0);
        }
    }

    private String summarizeInBatches(List<Message> messages, int summaryMaxTokens, int batchMaxTokens) {
        List<List<Message>> batches = new ArrayList<>();
        List<Message> current = new ArrayList<>();
        int currentTokens = 0;
        for (Message message : messages) {
            int messageTokens = this.tokenEstimator.estimate(message);
            if (!current.isEmpty() && currentTokens + messageTokens > batchMaxTokens) {
                batches.add(List.copyOf(current));
                current.clear();
                currentTokens = 0;
            }
            current.add(message);
            currentTokens += messageTokens;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }

        List<String> partialSummaries = batches.stream()
                .map(batch -> this.compressor.summarize(batch, summaryMaxTokens))
                .filter(summary -> summary != null && !summary.isBlank())
                .toList();
        if (partialSummaries.isEmpty()) {
            return "";
        }
        if (partialSummaries.size() == 1) {
            return partialSummaries.get(0);
        }
        List<Message> summaryMessages = partialSummaries.stream()
                .map(summary -> (Message) new SystemMessage("Partial conversation summary:\n" + summary))
                .toList();
        return this.compressor.summarize(summaryMessages, summaryMaxTokens);
    }

    private int estimatePrompt(String systemPrompt, String userMessage, List<Message> history,
            Collection<ToolCallback> toolCallbacks) {
        return 8 + this.tokenEstimator.estimate(systemPrompt) + this.tokenEstimator.estimate(userMessage)
                + this.tokenEstimator.estimate(history) + estimateTools(toolCallbacks);
    }

    private int estimateTools(Collection<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return 0;
        }
        return toolCallbacks.stream().mapToInt(callback -> 8
                + this.tokenEstimator.estimate(callback.getToolDefinition().name())
                + this.tokenEstimator.estimate(callback.getToolDefinition().description())
                + this.tokenEstimator.estimate(callback.getToolDefinition().inputSchema())).sum();
    }

    private static AiContextUsage usage(int estimatedTokens, int maxContextTokens, boolean compressed,
            int tokensBefore, int messagesBefore, int messagesAfter, int summarizedMessages) {
        return AiContextUsage.estimated(estimatedTokens, maxContextTokens, compressed, tokensBefore,
                messagesBefore, messagesAfter, summarizedMessages);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
