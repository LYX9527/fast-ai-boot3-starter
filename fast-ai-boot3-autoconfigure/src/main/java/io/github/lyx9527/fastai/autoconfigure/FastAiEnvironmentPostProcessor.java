package io.github.lyx9527.fastai.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将 starter 统一配置映射为具体 Provider 的 Spring AI 原生配置。
 * 业务显式声明的 {@code spring.ai.*} 配置始终具有更高优先级。
 */
public final class FastAiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 注入 Spring Environment 的低优先级属性源名称。 */
    private static final String PROPERTY_SOURCE_NAME = "fastAiProviderDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("fast.ai.enabled", Boolean.class, true)) {
            return;
        }
        String providerValue = environment.getProperty("fast.ai.provider", "openai");
        FastAiProvider provider = parseProvider(providerValue);
        String prefix = provider.usesDeepSeekStarter() ? "spring.ai.deepseek" : "spring.ai.openai";

        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("spring.ai.model.chat", provider.usesDeepSeekStarter() ? "deepseek" : "openai");
        copy(environment, mapped, "fast.ai.api-key", prefix + ".api-key");
        copy(environment, mapped, "fast.ai.base-url", prefix + ".base-url");
        copy(environment, mapped, "fast.ai.model", prefix + ".chat.options.model");
        copy(environment, mapped, "fast.ai.chat.temperature", prefix + ".chat.options.temperature");
        copy(environment, mapped, "fast.ai.chat.max-tokens", prefix + ".chat.options.max-tokens");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, mapped));
    }

    private static FastAiProvider parseProvider(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("COMPATIBLE") || normalized.equals("OTHER")) {
            return FastAiProvider.OPENAI_COMPATIBLE;
        }
        return FastAiProvider.valueOf(normalized);
    }

    private static void copy(ConfigurableEnvironment environment, Map<String, Object> target,
            String sourceKey, String targetKey) {
        String value = environment.getProperty(sourceKey);
        if (StringUtils.hasText(value)) {
            target.put(targetKey, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
