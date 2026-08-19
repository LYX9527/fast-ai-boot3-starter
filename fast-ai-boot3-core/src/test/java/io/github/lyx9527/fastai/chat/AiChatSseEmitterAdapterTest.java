package io.github.lyx9527.fastai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatSseEmitterAdapterTest {

    @Test
    void sendsAllChunksAndCompletesEmitter() {
        RecordingSseEmitter emitter = new RecordingSseEmitter(false);
        AiChatService service = service(Flux.just(
                AiChatChunk.context(null),
                AiChatChunk.delta("你好"),
                AiChatChunk.complete(null)));

        AiChatSseEmitterAdapter.subscribe(service, request(), emitter);

        assertThat(emitter.sentEvents).hasValue(4);
        assertThat(emitter.completed).isTrue();
        assertThat(emitter.error).isNull();
    }

    @Test
    void cancelsUpstreamWhenSendingFails() {
        AtomicBoolean cancelled = new AtomicBoolean();
        RecordingSseEmitter emitter = new RecordingSseEmitter(2);
        AiChatService service = service(Flux.concat(
                Flux.just(AiChatChunk.delta("第一段")),
                Flux.<AiChatChunk>never().doOnCancel(() -> cancelled.set(true))));

        AiChatSseEmitterAdapter.subscribe(service, request(), emitter);

        assertThat(emitter.error).isInstanceOf(IOException.class);
        assertThat(cancelled).isTrue();
    }

    private AiChatService service(Flux<AiChatChunk> chunks) {
        return new AiChatService() {
            @Override
            public AiChatResponse chat(AiChatRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<AiChatChunk> stream(AiChatRequest request) {
                return chunks;
            }

            @Override
            public void clearConversation(String tenantId, String userId, String conversationId) {
            }
        };
    }

    private AiChatRequest request() {
        return AiChatRequest.builder()
                .message("你好")
                .userId("sse-user")
                .conversationId("sse-conversation")
                .build();
    }

    private static final class RecordingSseEmitter extends SseEmitter {

        private final AtomicInteger sentEvents = new AtomicInteger();
        private final int failOnSendNumber;
        private boolean completed;
        private Throwable error;

        private RecordingSseEmitter(boolean failOnSend) {
            this(failOnSend ? 1 : -1);
        }

        private RecordingSseEmitter(int failOnSendNumber) {
            this.failOnSendNumber = failOnSendNumber;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            int sendNumber = this.sentEvents.incrementAndGet();
            if (sendNumber == this.failOnSendNumber) {
                throw new IOException("客户端连接已断开");
            }
        }

        @Override
        public void complete() {
            this.completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            this.error = ex;
        }
    }
}
