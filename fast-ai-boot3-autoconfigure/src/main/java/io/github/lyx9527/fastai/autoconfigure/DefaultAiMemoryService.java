package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.memory.*;
import org.springframework.ai.chat.memory.ChatMemory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务系统主动查询、保存和清理记忆的默认服务实现。
 */
final class DefaultAiMemoryService implements AiMemoryService {

    /** 长期记忆持久化实现。 */
    private final AiLongTermMemoryStore longTermMemoryStore;
    /** 短期会话窗口。 */
    private final ChatMemory chatMemory;
    /** 会话作用域 Key 工厂。 */
    private final AiConversationKeyFactory conversationKeyFactory;

    DefaultAiMemoryService(AiLongTermMemoryStore longTermMemoryStore, ChatMemory chatMemory,
            AiConversationKeyFactory conversationKeyFactory) {
        this.longTermMemoryStore = longTermMemoryStore;
        this.chatMemory = chatMemory;
        this.conversationKeyFactory = conversationKeyFactory;
    }

    @Override
    public List<AiMemoryItem> search(AiMemoryScope scope, String query, int limit) {
        return this.longTermMemoryStore.search(scope, query, limit);
    }

    @Override
    public AiMemoryItem remember(AiMemoryScope scope, String content, String memoryType, Duration ttl,
            Map<String, Object> metadata) {
        Instant now = Instant.now();
        AiMemoryItem item = new AiMemoryItem(UUID.randomUUID().toString(), scope.tenantId(), scope.userId(),
                content, memoryType, scope.conversationId(), now, ttl == null ? null : now.plus(ttl), metadata);
        this.longTermMemoryStore.save(item);
        return item;
    }

    @Override
    public void clearConversation(AiMemoryScope scope) {
        this.chatMemory.clear(this.conversationKeyFactory.create(scope));
    }

    @Override
    public void clearUser(String tenantId, String userId) {
        this.longTermMemoryStore.deleteByUser(tenantId, userId);
    }
}
