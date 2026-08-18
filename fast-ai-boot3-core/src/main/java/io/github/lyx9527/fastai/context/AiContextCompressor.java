package io.github.lyx9527.fastai.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 将较早的会话消息压缩为可继续对话的摘要。
 */
public interface AiContextCompressor {

    /**
     * 对消息列表生成摘要。
     *
     * @param messages 需要压缩的消息
     * @param maxSummaryTokens 摘要最大近似 Token 数
     * @return 摘要文本
     */
    String summarize(List<Message> messages, int maxSummaryTokens);
}
