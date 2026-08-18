package io.github.lyx9527.fastai.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 使用 SHA-256 创建稳定的会话存储 Key，避免在数据库主键中直接暴露租户和用户标识。
 */
public final class Sha256ConversationKeyFactory implements AiConversationKeyFactory {

    @Override
    public String create(AiMemoryScope scope) {
        String raw = scope.tenantId() + '\u001f' + scope.userId() + '\u001f' + scope.conversationId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
