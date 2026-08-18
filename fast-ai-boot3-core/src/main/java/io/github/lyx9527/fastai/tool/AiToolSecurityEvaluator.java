package io.github.lyx9527.fastai.tool;

/**
 * Tool 执行前的可替换安全授权接口。
 */
public interface AiToolSecurityEvaluator {

    /**
     * 校验 Tool 是否允许执行；拒绝时抛出运行时异常。
     *
     * @param request 授权请求
     */
    void authorize(AiToolAuthorizationRequest request);
}
