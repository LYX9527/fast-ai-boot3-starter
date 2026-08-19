package io.github.lyx9527.fastai.chat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 带租户、用户和会话作用域的统一对话请求。
 *
 * @param message 当前用户消息
 * @param tenantId 租户标识；为空时由自动配置使用默认租户
 * @param userId 用户标识
 * @param conversationId 会话标识
 * @param toolNames 本次请求允许注入的 Tool 名称
 * @param toolGroups 本次请求允许注入的 Tool 分组
 * @param toolSets 本次请求允许注入的工具集
 * @param toolsEnabled 本次请求是否允许显式或 LLM 语义路由方式注入 Tool
 * @param permissions 服务端鉴权后授予的权限
 * @param confirmedToolNames 已完成显式确认的 Tool 名称
 * @param metadata 请求级扩展元数据
 */
public record AiChatRequest(
        String message,
        String tenantId,
        String userId,
        String conversationId,
        Set<String> toolNames,
        Set<String> toolGroups,
        Set<String> toolSets,
        boolean toolsEnabled,
        Set<String> permissions,
        Set<String> confirmedToolNames,
        Map<String, Object> metadata) {

    public AiChatRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        toolNames = toolNames == null ? Set.of() : Set.copyOf(toolNames);
        toolGroups = toolGroups == null ? Set.of() : Set.copyOf(toolGroups);
        toolSets = toolSets == null ? Set.of() : Set.copyOf(toolSets);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        confirmedToolNames = confirmedToolNames == null ? Set.of() : Set.copyOf(confirmedToolNames);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public AiChatRequest(String message, String tenantId, String userId, String conversationId,
            Set<String> toolNames, Set<String> toolGroups, Map<String, Object> metadata) {
        this(message, tenantId, userId, conversationId, toolNames, toolGroups, Set.of(), true, Set.of(), Set.of(),
                metadata);
    }

    public AiChatRequest(String message, String tenantId, String userId, String conversationId,
            Set<String> toolNames, Set<String> toolGroups, Set<String> toolSets,
            Set<String> permissions, Set<String> confirmedToolNames, Map<String, Object> metadata) {
        this(message, tenantId, userId, conversationId, toolNames, toolGroups, toolSets, true,
                permissions, confirmedToolNames, metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link AiChatRequest} 的链式构建器。
     */
    public static final class Builder {

        /** 当前用户消息。 */
        private String message;
        /** 租户标识。 */
        private String tenantId;
        /** 用户标识。 */
        private String userId;
        /** 会话标识。 */
        private String conversationId;
        /** 按名称选择的 Tool。 */
        private final Set<String> toolNames = new LinkedHashSet<>();
        /** 按分组选择的 Tool。 */
        private final Set<String> toolGroups = new LinkedHashSet<>();
        /** 按工具集选择的 Tool。 */
        private final Set<String> toolSets = new LinkedHashSet<>();
        /** 本次请求是否允许注入 Tool。 */
        private boolean toolsEnabled = true;
        /** 服务端授予的权限集合。 */
        private final Set<String> permissions = new LinkedHashSet<>();
        /** 已确认执行的具体 Tool 名称。 */
        private final Set<String> confirmedToolNames = new LinkedHashSet<>();
        /** 请求扩展元数据。 */
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder toolNames(Set<String> toolNames) {
            this.toolNames.clear();
            if (toolNames != null) {
                this.toolNames.addAll(toolNames);
            }
            return this;
        }

        public Builder addTool(String toolName) {
            this.toolNames.add(toolName);
            return this;
        }

        public Builder toolGroups(Set<String> toolGroups) {
            this.toolGroups.clear();
            if (toolGroups != null) {
                this.toolGroups.addAll(toolGroups);
            }
            return this;
        }

        public Builder addToolGroup(String toolGroup) {
            this.toolGroups.add(toolGroup);
            return this;
        }

        public Builder toolSets(Set<String> toolSets) {
            this.toolSets.clear();
            if (toolSets != null) {
                this.toolSets.addAll(toolSets);
            }
            return this;
        }

        public Builder addToolSet(String toolSet) {
            this.toolSets.add(toolSet);
            return this;
        }

        public Builder toolsEnabled(boolean toolsEnabled) {
            this.toolsEnabled = toolsEnabled;
            return this;
        }

        /**
         * 设置由业务系统认证和鉴权上下文解析出的权限集合。
         *
         * @param permissions 服务端可信权限集合
         * @return 当前构建器
         */
        public Builder permissions(Set<String> permissions) {
            this.permissions.clear();
            if (permissions != null) {
                this.permissions.addAll(permissions);
            }
            return this;
        }

        public Builder addPermission(String permission) {
            this.permissions.add(permission);
            return this;
        }

        public Builder confirmedToolNames(Set<String> confirmedToolNames) {
            this.confirmedToolNames.clear();
            if (confirmedToolNames != null) {
                this.confirmedToolNames.addAll(confirmedToolNames);
            }
            return this;
        }

        public Builder confirmTool(String toolName) {
            this.confirmedToolNames.add(toolName);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public AiChatRequest build() {
            return new AiChatRequest(this.message, this.tenantId, this.userId, this.conversationId,
                    this.toolNames, this.toolGroups, this.toolSets, this.toolsEnabled,
                    this.permissions, this.confirmedToolNames, this.metadata);
        }
    }
}
