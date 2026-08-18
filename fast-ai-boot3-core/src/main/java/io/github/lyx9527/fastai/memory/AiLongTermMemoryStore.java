package io.github.lyx9527.fastai.memory;

import java.util.List;

/**
 * 用户长期记忆持久化与检索接口。
 */
public interface AiLongTermMemoryStore {

    /** 保存或按内容去重更新一条长期记忆。 */
    void save(AiMemoryItem memory);

    /** 按租户和用户作用域检索相关长期记忆。 */
    List<AiMemoryItem> search(AiMemoryScope scope, String query, int limit);

    /** 删除指定租户和用户的全部长期记忆。 */
    void deleteByUser(String tenantId, String userId);
}
