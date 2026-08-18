package io.github.lyx9527.fastai.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FastAiEnvironmentPostProcessorTest {

    @Test
    void mapsDeepSeekPropertiesToDeepSeekStarter() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "fast.ai.provider", "deepseek",
                "fast.ai.api-key", "test-key",
                "fast.ai.model", "deepseek-chat")));

        new FastAiEnvironmentPostProcessor().postProcessEnvironment(environment,
                new SpringApplication(Object.class));

        assertEquals("deepseek", environment.getProperty("spring.ai.model.chat"));
        assertEquals("test-key", environment.getProperty("spring.ai.deepseek.api-key"));
        assertEquals("deepseek-chat", environment.getProperty("spring.ai.deepseek.chat.options.model"));
    }

    @Test
    void mapsCompatibleProvidersToOpenAiStarter() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "fast.ai.provider", "openai-compatible",
                "fast.ai.api-key", "test-key",
                "fast.ai.base-url", "https://example.test")));

        new FastAiEnvironmentPostProcessor().postProcessEnvironment(environment,
                new SpringApplication(Object.class));

        assertEquals("openai", environment.getProperty("spring.ai.model.chat"));
        assertEquals("test-key", environment.getProperty("spring.ai.openai.api-key"));
        assertEquals("https://example.test", environment.getProperty("spring.ai.openai.base-url"));
    }
}
