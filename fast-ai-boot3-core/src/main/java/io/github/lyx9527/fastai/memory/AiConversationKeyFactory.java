package io.github.lyx9527.fastai.memory;

/**
 * 将租户、用户和会话作用域转换为持久化会话 Key。
 */
public interface AiConversationKeyFactory {

    /**
     * 创建稳定的会话存储 Key。
     *
     * @param scope 记忆作用域
     * @return 会话存储 Key
     */
    String create(AiMemoryScope scope);
}
