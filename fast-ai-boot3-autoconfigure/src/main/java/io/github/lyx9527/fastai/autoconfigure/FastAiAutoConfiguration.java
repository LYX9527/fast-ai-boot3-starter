package io.github.lyx9527.fastai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.lyx9527.fastai.chat.AiChatService;
import io.github.lyx9527.fastai.context.AiContextCompressor;
import io.github.lyx9527.fastai.context.AiContextManager;
import io.github.lyx9527.fastai.context.AiTokenEstimator;
import io.github.lyx9527.fastai.context.HeuristicAiTokenEstimator;
import io.github.lyx9527.fastai.intent.AiIntentDefinition;
import io.github.lyx9527.fastai.intent.IntentRecognitionService;
import io.github.lyx9527.fastai.memory.*;
import io.github.lyx9527.fastai.tool.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fast AI Starter 的 Spring Boot 自动配置入口。
 * 负责装配 Provider、持久化记忆、上下文压缩、Tool、安全、意图识别和统一对话服务。
 */
@AutoConfiguration(afterName = {
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
        "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
        "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
        "org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
@ConditionalOnClass({ChatClient.class, ChatModel.class})
@ConditionalOnProperty(prefix = "fast.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FastAiProperties.class)
public class FastAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiConversationKeyFactory aiConversationKeyFactory() {
        return new Sha256ConversationKeyFactory();
    }

    @Bean(name = "fastAiPersistenceDataSource")
    @ConditionalOnMissingBean(name = "fastAiPersistenceDataSource")
    @ConditionalOnProperty(prefix = "fast.ai.persistence", name = "type", havingValue = "sqlite",
            matchIfMissing = true)
    public DataSource fastAiSqliteDataSource(FastAiProperties properties) {
        FastAiProperties.Persistence.Sqlite sqlite = properties.getPersistence().getSqlite();
        String configuredFile = sqlite.getFile();
        if (configuredFile == null || configuredFile.isBlank()) {
            throw new IllegalStateException("fast.ai.persistence.sqlite.file must not be blank");
        }
        String normalizedFile = configuredFile.toLowerCase(Locale.ROOT);
        if (normalizedFile.contains(":memory:") || normalizedFile.contains("mode=memory")) {
            throw new IllegalStateException("In-memory SQLite is not supported; configure a file-backed database");
        }
        String jdbcUrl = configuredFile.startsWith("jdbc:sqlite:")
                ? configuredFile
                : "jdbc:sqlite:" + absolutePath(configuredFile);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(Math.max(1, sqlite.getMaximumPoolSize()));
        config.setPoolName("fast-ai-sqlite");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("journal_mode", "WAL");
        return new HikariDataSource(config);
    }

    @Bean(name = "fastAiPersistenceDataSource")
    @ConditionalOnMissingBean(name = "fastAiPersistenceDataSource")
    @ConditionalOnProperty(prefix = "fast.ai.persistence", name = "type", havingValue = "mysql")
    public DataSource fastAiMysqlDataSource(FastAiProperties properties) {
        FastAiProperties.Persistence.Mysql mysql = properties.getPersistence().getMysql();
        if (mysql.getUrl() == null || mysql.getUrl().isBlank()) {
            throw new IllegalStateException(
                    "MySQL storage requires fast.ai.persistence.mysql.url or a custom fastAiPersistenceDataSource bean");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysql.getUrl());
        if (mysql.getUsername() != null) {
            config.setUsername(mysql.getUsername());
        }
        if (mysql.getPassword() != null) {
            config.setPassword(mysql.getPassword());
        }
        if (mysql.getDriverClassName() != null && !mysql.getDriverClassName().isBlank()) {
            config.setDriverClassName(mysql.getDriverClassName());
        }
        config.setMaximumPoolSize(Math.max(1, mysql.getMaximumPoolSize()));
        config.setPoolName("fast-ai-mysql");
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatMemoryRepository chatMemoryRepository(
            @Qualifier("fastAiPersistenceDataSource") DataSource dataSource,
            ObjectProvider<ObjectMapper> objectMappers) {
        return new JdbcChatMemoryRepository(dataSource, objectMapper(objectMappers));
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatMemory chatMemory(ChatMemoryRepository repository, FastAiProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.getMemory().getShortTerm().getMaxMessages())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiLongTermMemoryStore aiLongTermMemoryStore(
            @Qualifier("fastAiPersistenceDataSource") DataSource dataSource,
            ObjectProvider<ObjectMapper> objectMappers) {
        return new JdbcAiLongTermMemoryStore(dataSource, objectMapper(objectMappers));
    }

    @Bean
    @ConditionalOnMissingBean
    public AiTokenEstimator aiTokenEstimator() {
        return new HeuristicAiTokenEstimator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiToolSecurityEvaluator aiToolSecurityEvaluator() {
        return new DefaultAiToolSecurityEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiToolRegistry aiToolRegistry(ObjectProvider<AiGeneratedTool> generatedTools,
            AiToolSecurityEvaluator securityEvaluator) {
        return new DefaultAiToolRegistry(generatedTools.orderedStream().toList(), securityEvaluator);
    }

    @Bean("fastAiChatClient")
    @ConditionalOnBean(ChatClient.Builder.class)
    @ConditionalOnMissingBean(name = "fastAiChatClient")
    public ChatClient fastAiChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "fastAiMemoryExecutor")
    public ExecutorService fastAiMemoryExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "fast-ai-memory-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(2, factory);
    }

    @Bean
    @ConditionalOnBean(name = "fastAiChatClient")
    @ConditionalOnMissingBean
    public AiMemoryExtractor aiMemoryExtractor(@Qualifier("fastAiChatClient") ChatClient chatClient) {
        return new LlmAiMemoryExtractor(chatClient);
    }

    @Bean
    @ConditionalOnBean(name = "fastAiChatClient")
    @ConditionalOnMissingBean
    public AiContextCompressor aiContextCompressor(@Qualifier("fastAiChatClient") ChatClient chatClient) {
        return new LlmAiContextCompressor(chatClient);
    }

    @Bean
    @ConditionalOnBean(AiContextCompressor.class)
    @ConditionalOnMissingBean
    public AiContextManager aiContextManager(ChatMemory chatMemory, AiTokenEstimator tokenEstimator,
            AiContextCompressor compressor, FastAiProperties properties) {
        return new DefaultAiContextManager(chatMemory, tokenEstimator, compressor, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiMemoryService aiMemoryService(AiLongTermMemoryStore longTermMemoryStore, ChatMemory chatMemory,
            AiConversationKeyFactory conversationKeyFactory) {
        return new DefaultAiMemoryService(longTermMemoryStore, chatMemory, conversationKeyFactory);
    }

    @Bean
    @ConditionalOnBean(name = "fastAiChatClient")
    @ConditionalOnMissingBean
    public AiChatService aiChatService(@Qualifier("fastAiChatClient") ChatClient chatClient, ChatMemory chatMemory,
            MessageChatMemoryAdvisor memoryAdvisor, AiConversationKeyFactory conversationKeyFactory,
            AiLongTermMemoryStore longTermMemoryStore, AiMemoryExtractor memoryExtractor,
            AiToolRegistry toolRegistry, AiContextManager contextManager,
            @Qualifier("fastAiMemoryExecutor") ExecutorService memoryExecutor, FastAiProperties properties) {
        return new DefaultAiChatService(chatClient, chatMemory, memoryAdvisor, conversationKeyFactory,
                longTermMemoryStore, memoryExtractor, toolRegistry, contextManager, memoryExecutor, properties);
    }

    @Bean
    @ConditionalOnBean(name = "fastAiChatClient")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "fast.ai.intent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IntentRecognitionService intentRecognitionService(
            @Qualifier("fastAiChatClient") ChatClient chatClient,
            ObjectProvider<AiIntentDefinition> customDefinitions, FastAiProperties properties) {
        List<AiIntentDefinition> definitions = new ArrayList<>(customDefinitions.orderedStream().toList());
        properties.getIntent().getDefinitions().stream()
                .filter(definition -> definition.getCode() != null && definition.getDescription() != null)
                .map(definition -> new AiIntentDefinition(definition.getCode(), definition.getDescription(),
                        definition.getExamples(), definition.getRequiredSlots()))
                .forEach(definitions::add);
        return new DefaultIntentRecognitionService(chatClient, definitions,
                properties.getIntent().getConfidenceThreshold());
    }

    @Bean
    public SmartInitializingSingleton fastAiProviderDependencyValidator(ObjectProvider<ChatModel> chatModel,
            FastAiProperties properties) {
        return () -> {
            if (chatModel.getIfAvailable() == null) {
                String dependency = properties.getProvider().usesDeepSeekStarter()
                        ? "org.springframework.ai:spring-ai-starter-model-deepseek"
                        : "org.springframework.ai:spring-ai-starter-model-openai";
                throw new IllegalStateException("No ChatModel bean found for fast.ai.provider="
                        + properties.getProvider() + ". Add dependency: " + dependency);
            }
        };
    }

    private static String absolutePath(String configuredFile) {
        try {
            Path path = Path.of(configuredFile).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return path.toString();
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare SQLite database path: " + configuredFile, exception);
        }
    }

    private static ObjectMapper objectMapper(ObjectProvider<ObjectMapper> objectMappers) {
        ObjectMapper objectMapper = objectMappers.getIfAvailable();
        return objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }
}
