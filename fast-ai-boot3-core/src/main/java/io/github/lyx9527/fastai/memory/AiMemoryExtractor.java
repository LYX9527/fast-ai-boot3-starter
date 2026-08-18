package io.github.lyx9527.fastai.memory;

import java.util.List;

/**
 * 从一轮对话中提取稳定用户事实和偏好的接口。
 */
public interface AiMemoryExtractor {

    /**
     * 提取值得长期保存的记忆文本。
     *
     * @param userMessage 用户消息
     * @param assistantResponse 助手回复
     * @return 可持久化的记忆文本集合
     */
    List<String> extract(String userMessage, String assistantResponse);
}
