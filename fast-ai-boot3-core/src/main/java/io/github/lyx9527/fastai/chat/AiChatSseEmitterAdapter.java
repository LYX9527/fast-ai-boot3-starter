package io.github.lyx9527.fastai.chat;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将 {@link AiChatService} 的 Reactor 流转换为 Spring MVC {@link SseEmitter}。
 */
public final class AiChatSseEmitterAdapter {

    /** SSE 事件 ID 的初始值。 */
    private static final long INITIAL_EVENT_ID = 0L;
    /** 默认 SSE 连接超时，避免慢模型被容器常见的 30 秒异步超时提前中断。 */
    private static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;

    private AiChatSseEmitterAdapter() {
    }

    /**
     * 使用 5 分钟默认超时创建 SSE 输出器。
     *
     * @param chatService AI 对话服务
     * @param request 对话请求
     * @return 已开始输出对话事件的 SSE 输出器
     */
    public static SseEmitter create(AiChatService chatService, AiChatRequest request) {
        return subscribe(chatService, request, new SseEmitter(DEFAULT_TIMEOUT_MILLIS));
    }

    /**
     * 使用指定超时时间创建 SSE 输出器。
     *
     * @param chatService AI 对话服务
     * @param request 对话请求
     * @param timeoutMillis SSE 连接超时毫秒数；小于或等于 0 表示不单独设置超时
     * @return 已开始输出对话事件的 SSE 输出器
     */
    public static SseEmitter create(AiChatService chatService, AiChatRequest request, long timeoutMillis) {
        SseEmitter emitter = timeoutMillis > 0 ? new SseEmitter(timeoutMillis) : new SseEmitter();
        return subscribe(chatService, request, emitter);
    }

    static SseEmitter subscribe(AiChatService chatService, AiChatRequest request, SseEmitter emitter) {
        Objects.requireNonNull(chatService, "chatService must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(emitter, "emitter must not be null");

        AtomicBoolean terminated = new AtomicBoolean();
        AtomicLong eventId = new AtomicLong(INITIAL_EVENT_ID);
        AtomicReference<Disposable> subscription = new AtomicReference<>();

        Runnable cancelSubscription = () -> {
            terminated.set(true);
            Disposable disposable = subscription.get();
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        };
        emitter.onCompletion(cancelSubscription);
        emitter.onTimeout(() -> {
            cancelSubscription.run();
            emitter.complete();
        });
        emitter.onError(error -> cancelSubscription.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        }
        catch (Exception exception) {
            terminated.set(true);
            emitter.completeWithError(exception);
            return emitter;
        }

        Disposable disposable = chatService.stream(request).subscribe(
                chunk -> send(emitter, chunk, eventId, terminated, subscription),
                error -> completeWithError(emitter, error, terminated),
                () -> complete(emitter, terminated));
        subscription.set(disposable);
        if (terminated.get() && !disposable.isDisposed()) {
            disposable.dispose();
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, AiChatChunk chunk, AtomicLong eventId,
            AtomicBoolean terminated, AtomicReference<Disposable> subscription) {
        if (terminated.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(eventId.incrementAndGet()))
                    .name(chunk.eventType().name().toLowerCase(Locale.ROOT))
                    .data(chunk, MediaType.APPLICATION_JSON));
        }
        catch (Exception exception) {
            if (terminated.compareAndSet(false, true)) {
                Disposable disposable = subscription.get();
                if (disposable != null) {
                    disposable.dispose();
                }
                emitter.completeWithError(exception);
            }
        }
    }

    private static void complete(SseEmitter emitter, AtomicBoolean terminated) {
        if (terminated.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private static void completeWithError(SseEmitter emitter, Throwable error, AtomicBoolean terminated) {
        if (terminated.compareAndSet(false, true)) {
            emitter.completeWithError(error);
        }
    }
}
