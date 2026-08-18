package io.github.lyx9527.fastai.context;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Provider 无关的 Token 数量估算接口。
 */
public interface AiTokenEstimator {

    /**
     * 估算文本 Token 数。
     *
     * @param text 文本
     * @return 估算 Token 数
     */
    int estimate(String text);

    default int estimate(Message message) {
        return 4 + estimate(message == null ? null : message.getText());
    }

    default int estimate(List<Message> messages) {
        if (messages == null) {
            return 0;
        }
        return messages.stream().mapToInt(this::estimate).sum();
    }
}
