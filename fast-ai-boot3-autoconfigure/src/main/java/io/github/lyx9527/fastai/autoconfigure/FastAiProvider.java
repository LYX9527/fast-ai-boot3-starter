package io.github.lyx9527.fastai.autoconfigure;

/**
 * starter 支持的模型 Provider 类型。
 */
public enum FastAiProvider {
    /** OpenAI 官方服务。 */
    OPENAI,
    /** 使用 Spring AI DeepSeek starter 的 DeepSeek 服务。 */
    DEEPSEEK,
    /** 通过 OpenAI 协议和自定义 Base URL 接入的兼容服务。 */
    OPENAI_COMPATIBLE;

    public boolean usesDeepSeekStarter() {
        return this == DEEPSEEK;
    }
}
