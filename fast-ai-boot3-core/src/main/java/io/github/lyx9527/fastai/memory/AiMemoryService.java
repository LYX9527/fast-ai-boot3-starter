package io.github.lyx9527.fastai.memory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 提供给业务系统主动管理短期会话与长期记忆的服务。
 */
public interface AiMemoryService {

    /** 检索当前用户相关的长期记忆。 */
    List<AiMemoryItem> search(AiMemoryScope scope, String query, int limit);

    /** 主动保存一条长期记忆。 */
    AiMemoryItem remember(AiMemoryScope scope, String content, String memoryType, Duration ttl,
            Map<String, Object> metadata);

    /** 清理指定会话的短期历史。 */
    void clearConversation(AiMemoryScope scope);

    /** 清理指定租户和用户的全部长期记忆。 */
    void clearUser(String tenantId, String userId);
}
