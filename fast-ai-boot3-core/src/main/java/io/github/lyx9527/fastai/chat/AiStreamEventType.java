package io.github.lyx9527.fastai.chat;

/**
 * 流式对话事件类型。
 */
public enum AiStreamEventType {
    /** 正式生成前的上下文占用事件。 */
    CONTEXT,
    /** 模型生成的文本增量事件。 */
    DELTA,
    /** 流结束及最终 Token 占用事件。 */
    COMPLETE
}
