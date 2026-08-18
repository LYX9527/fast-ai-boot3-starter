package io.github.lyx9527.fastai.exception;

/**
 * starter 对外抛出的统一运行时异常。
 */
public class FastAiException extends RuntimeException {

    /** 稳定的业务错误码。 */
    private final String code;

    public FastAiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public FastAiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }
}
