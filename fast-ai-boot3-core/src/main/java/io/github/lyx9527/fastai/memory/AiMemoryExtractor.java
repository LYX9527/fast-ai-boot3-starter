package io.github.lyx9527.fastai.memory;

import java.util.List;

/**
 * 从一轮对话中提取稳定用户事实和偏好的接口。
 * 默认实现不调用模型；业务系统可以声明自定义 Bean 接入独立的规则、模型或数据治理流程。
 */
public interface AiMemoryExtractor {

    /**
     * 提取值得长期保存的记忆文本。
     *
     * @param userMessage 用户消息
     * @param assistantResponse 助手回复；默认实现不会使用该内容，避免保存模型推断信息
     * @return 可持久化的记忆文本集合
     */
    List<String> extract(String userMessage, String assistantResponse);
}
