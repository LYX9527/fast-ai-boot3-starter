package io.github.lyx9527.fastai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fast.ai} 前缀下的全部 starter 配置。
 */
@ConfigurationProperties("fast.ai")
public class FastAiProperties {

    /** 是否启用 Fast AI 自动配置。 */
    private boolean enabled = true;
    /** 当前使用的模型 Provider。 */
    private FastAiProvider provider = FastAiProvider.OPENAI;
    /** 模型 Provider API Key。 */
    private String apiKey;
    /** OpenAI 协议兼容服务的基础地址。 */
    private String baseUrl;
    /** 默认聊天模型名称。 */
    private String model;
    /** 请求未传租户标识时使用的默认租户。 */
    private String defaultTenantId = "default";
    /** 对话生成配置。 */
    private final Chat chat = new Chat();
    /** 长上下文管理配置。 */
    private final Context context = new Context();
    /** 长短期记忆配置。 */
    private final Memory memory = new Memory();
    /** 对话历史和长期记忆持久化配置。 */
    private final Persistence persistence = new Persistence();
    /** Tool 注册与选择配置。 */
    private final Tools tools = new Tools();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FastAiProvider getProvider() {
        return this.provider;
    }

    public void setProvider(FastAiProvider provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDefaultTenantId() {
        return this.defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public Chat getChat() {
        return this.chat;
    }

    public Context getContext() {
        return this.context;
    }

    public Memory getMemory() {
        return this.memory;
    }

    public Persistence getPersistence() {
        return this.persistence;
    }

    public Tools getTools() {
        return this.tools;
    }

    /**
     * 模型对话生成配置。
     */
    public static class Chat {

        /** 应用于全部对话请求的系统提示词。 */
        private String systemPrompt = "You are a reliable enterprise AI assistant.";
        /** 模型采样温度；为空时使用 Provider 默认值。 */
        private Double temperature;
        /** 单次回复最大 Token 数；为空时使用 Provider 默认值。 */
        private Integer maxTokens;

        public String getSystemPrompt() {
            return this.systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public Double getTemperature() {
            return this.temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return this.maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    /**
     * 长上下文占用估算和压缩配置。
     */
    public static class Context {

        /** 是否在超过阈值时自动压缩会话历史。 */
        private boolean compressionEnabled = true;
        /** 模型最大上下文 Token 数。 */
        private int maxContextTokens = 64_000;
        /** 为模型输出预留的 Token 数。 */
        private int reservedOutputTokens = 4_096;
        /** 触发压缩的 Prompt 预算占比。 */
        private double compressionThreshold = 0.8;
        /** 压缩完成后的目标上下文占比。 */
        private double targetOccupancy = 0.55;
        /** 压缩时始终保留的最近消息数量。 */
        private int preserveRecentMessages = 8;
        /** 单次提交给压缩模型的最大近似 Token 数。 */
        private int compressionBatchTokens = 12_000;
        /** 单次摘要允许的最大近似 Token 数。 */
        private int summaryMaxTokens = 2_000;

        public boolean isCompressionEnabled() {
            return this.compressionEnabled;
        }

        public void setCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
        }

        public int getMaxContextTokens() {
            return this.maxContextTokens;
        }

        public void setMaxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
        }

        public int getReservedOutputTokens() {
            return this.reservedOutputTokens;
        }

        public void setReservedOutputTokens(int reservedOutputTokens) {
            this.reservedOutputTokens = reservedOutputTokens;
        }

        public double getCompressionThreshold() {
            return this.compressionThreshold;
        }

        public void setCompressionThreshold(double compressionThreshold) {
            this.compressionThreshold = compressionThreshold;
        }

        public double getTargetOccupancy() {
            return this.targetOccupancy;
        }

        public void setTargetOccupancy(double targetOccupancy) {
            this.targetOccupancy = targetOccupancy;
        }

        public int getPreserveRecentMessages() {
            return this.preserveRecentMessages;
        }

        public void setPreserveRecentMessages(int preserveRecentMessages) {
            this.preserveRecentMessages = preserveRecentMessages;
        }

        public int getCompressionBatchTokens() {
            return this.compressionBatchTokens;
        }

        public void setCompressionBatchTokens(int compressionBatchTokens) {
            this.compressionBatchTokens = compressionBatchTokens;
        }

        public int getSummaryMaxTokens() {
            return this.summaryMaxTokens;
        }

        public void setSummaryMaxTokens(int summaryMaxTokens) {
            this.summaryMaxTokens = summaryMaxTokens;
        }
    }

    /**
     * 短期会话和长期用户记忆配置。
     */
    public static class Memory {

        /** 短期会话窗口配置。 */
        private final ShortTerm shortTerm = new ShortTerm();
        /** 长期用户记忆配置。 */
        private final LongTerm longTerm = new LongTerm();

        public ShortTerm getShortTerm() {
            return this.shortTerm;
        }

        public LongTerm getLongTerm() {
            return this.longTerm;
        }
    }

    /**
     * 对话历史和长期记忆的数据库持久化配置。
     */
    public static class Persistence {

        /** 数据库类型，默认使用文件型 SQLite。 */
        private StorageType type = StorageType.SQLITE;
        /** SQLite 专用配置。 */
        private final Sqlite sqlite = new Sqlite();
        /** MySQL 专用配置。 */
        private final Mysql mysql = new Mysql();

        public StorageType getType() {
            return this.type;
        }

        public void setType(StorageType type) {
            this.type = type;
        }

        public Sqlite getSqlite() {
            return this.sqlite;
        }

        public Mysql getMysql() {
            return this.mysql;
        }

        /**
         * SQLite 文件和连接池配置。
         */
        public static class Sqlite {

            /** SQLite 数据库文件路径。 */
            private String file = "data/fast-ai.db";
            /** SQLite Hikari 连接池最大连接数。 */
            private int maximumPoolSize = 4;

            public String getFile() {
                return this.file;
            }

            public void setFile(String file) {
                this.file = file;
            }

            public int getMaximumPoolSize() {
                return this.maximumPoolSize;
            }

            public void setMaximumPoolSize(int maximumPoolSize) {
                this.maximumPoolSize = maximumPoolSize;
            }
        }

        /**
         * MySQL JDBC 和连接池配置。
         */
        public static class Mysql {

            /** MySQL JDBC URL。 */
            private String url;
            /** MySQL 登录用户名。 */
            private String username;
            /** MySQL 登录密码。 */
            private String password;
            /** MySQL JDBC 驱动类名。 */
            private String driverClassName = "com.mysql.cj.jdbc.Driver";
            /** MySQL Hikari 连接池最大连接数。 */
            private int maximumPoolSize = 10;

            public String getUrl() {
                return this.url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public String getUsername() {
                return this.username;
            }

            public void setUsername(String username) {
                this.username = username;
            }

            public String getPassword() {
                return this.password;
            }

            public void setPassword(String password) {
                this.password = password;
            }

            public String getDriverClassName() {
                return this.driverClassName;
            }

            public void setDriverClassName(String driverClassName) {
                this.driverClassName = driverClassName;
            }

            public int getMaximumPoolSize() {
                return this.maximumPoolSize;
            }

            public void setMaximumPoolSize(int maximumPoolSize) {
                this.maximumPoolSize = maximumPoolSize;
            }
        }
    }

    /**
     * starter 内置支持的数据库类型。
     */
    public enum StorageType {
        /** 文件型 SQLite。 */
        SQLITE,
        /** MySQL 数据库。 */
        MYSQL
    }

    /**
     * 当前会话短期记忆配置。
     */
    public static class ShortTerm {

        /** 是否将短期记忆注入模型请求。 */
        private boolean enabled = true;
        /** 每个会话窗口最多保留的消息数量。 */
        private int maxMessages = 200;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxMessages() {
            return this.maxMessages;
        }

        public void setMaxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
        }
    }

    /**
     * 用户长期记忆配置。
     */
    public static class LongTerm {

        /** 是否在对话中检索和使用长期记忆。 */
        private boolean enabled = true;
        /** 是否在每轮对话完成后异步提取长期记忆。 */
        private boolean autoExtract = true;
        /** 每次对话检索的长期记忆最大数量。 */
        private int topK = 5;
        /** 每轮对话最多保存的自动提取记忆数量。 */
        private int maxExtractedPerTurn = 3;
        /** 自动提取记忆的可选存活时间；为空表示不过期。 */
        private Duration ttl;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoExtract() {
            return this.autoExtract;
        }

        public void setAutoExtract(boolean autoExtract) {
            this.autoExtract = autoExtract;
        }

        public int getTopK() {
            return this.topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public int getMaxExtractedPerTurn() {
            return this.maxExtractedPerTurn;
        }

        public void setMaxExtractedPerTurn(int maxExtractedPerTurn) {
            this.maxExtractedPerTurn = maxExtractedPerTurn;
        }

        public Duration getTtl() {
            return this.ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    /**
     * Tool 注册和请求级选择配置。
     */
    public static class Tools {

        /** 是否启用业务 Tool。 */
        private boolean enabled = true;
        /** 关闭语义路由后，请求未指定名称、分组或工具集时是否注入全部 Tool。 */
        private boolean includeAllWhenUnspecified = false;
        /** 基于 LLM 语义的请求级 Tool 自动选择配置。 */
        private final SemanticRouting semanticRouting = new SemanticRouting();

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeAllWhenUnspecified() {
            return this.includeAllWhenUnspecified;
        }

        public void setIncludeAllWhenUnspecified(boolean includeAllWhenUnspecified) {
            this.includeAllWhenUnspecified = includeAllWhenUnspecified;
        }

        public SemanticRouting getSemanticRouting() {
            return this.semanticRouting;
        }
    }

    /**
     * LLM Tool 语义路由配置。
     */
    public static class SemanticRouting {

        /** 是否启用 LLM Tool 语义选择。 */
        private boolean enabled = true;
        /** 接受 Tool 语义选择结果的最低置信度。 */
        private double confidenceThreshold = 0.7;
        /** 单轮语义路由最多注入的 Tool 数量。 */
        private int maxSelectedTools = 5;
        /** 单次语义路由请求最多携带的候选 Tool 数量。 */
        private int catalogBatchSize = 24;
        /** 语义路由时最多读取的最近历史消息数量。 */
        private int historyMessages = 6;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getConfidenceThreshold() {
            return this.confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public int getMaxSelectedTools() {
            return this.maxSelectedTools;
        }

        public void setMaxSelectedTools(int maxSelectedTools) {
            this.maxSelectedTools = maxSelectedTools;
        }

        public int getCatalogBatchSize() {
            return this.catalogBatchSize;
        }

        public void setCatalogBatchSize(int catalogBatchSize) {
            this.catalogBatchSize = catalogBatchSize;
        }

        public int getHistoryMessages() {
            return this.historyMessages;
        }

        public void setHistoryMessages(int historyMessages) {
            this.historyMessages = historyMessages;
        }
    }
}
