package io.github.lyx9527.fastai.chat;

import io.github.lyx9527.fastai.context.AiContextUsage;

/**
 * 流式对话事件数据。
 *
 * @param content 当前文本增量；上下文和完成事件为空字符串
 * @param finalChunk 是否为最终事件
 * @param eventType 事件类型
 * @param contextUsage 上下文窗口占用信息
 */
public record AiChatChunk(
        String content,
        boolean finalChunk,
        AiStreamEventType eventType,
        AiContextUsage contextUsage) {

    public AiChatChunk {
        content = content == null ? "" : content;
        eventType = eventType == null ? (finalChunk ? AiStreamEventType.COMPLETE : AiStreamEventType.DELTA)
                : eventType;
    }

    public AiChatChunk(String content, boolean finalChunk) {
        this(content, finalChunk, finalChunk ? AiStreamEventType.COMPLETE : AiStreamEventType.DELTA, null);
    }

    public static AiChatChunk context(AiContextUsage usage) {
        return new AiChatChunk("", false, AiStreamEventType.CONTEXT, usage);
    }

    public static AiChatChunk delta(String content) {
        return new AiChatChunk(content, false, AiStreamEventType.DELTA, null);
    }

    public static AiChatChunk complete(AiContextUsage usage) {
        return new AiChatChunk("", true, AiStreamEventType.COMPLETE, usage);
    }
}
