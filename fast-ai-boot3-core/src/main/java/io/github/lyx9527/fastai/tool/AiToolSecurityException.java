package io.github.lyx9527.fastai.tool;

/**
 * Tool 权限不足或缺少显式确认时抛出的安全异常。
 */
public final class AiToolSecurityException extends RuntimeException {

    public AiToolSecurityException(String message) {
        super(message);
    }
}
