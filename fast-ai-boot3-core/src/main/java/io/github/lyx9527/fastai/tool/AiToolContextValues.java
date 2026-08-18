package io.github.lyx9527.fastai.tool;

import org.springframework.ai.chat.model.ToolContext;

/**
 * 可信 ToolContext 的标准字段名称和类型安全读取工具。
 */
public final class AiToolContextValues {

    /** 当前租户标识。 */
    public static final String TENANT_ID = "tenantId";
    /** 当前用户标识。 */
    public static final String USER_ID = "userId";
    /** 当前会话标识。 */
    public static final String CONVERSATION_ID = "conversationId";
    /** 未提升为标准字段的原始请求 metadata。 */
    public static final String REQUEST_METADATA = "metadata";
    /** 服务端鉴权后授予的权限集合。 */
    public static final String PERMISSIONS = "permissions";
    /** 已完成显式确认的 Tool 名称集合。 */
    public static final String CONFIRMED_TOOLS = "confirmedTools";

    private AiToolContextValues() {
    }

    /**
     * 获取必需的 ToolContext 字段并校验目标类型。
     *
     * @param context ToolContext
     * @param key 字段名称
     * @param type 目标类型
     * @return 已校验并转换的字段值
     * @param <T> 目标类型
     */
    public static <T> T required(ToolContext context, String key, Class<T> type) {
        if (context == null) {
            throw new IllegalStateException("Tool context is required for key: " + key);
        }
        Object value = context.getContext().get(key);
        if (value == null) {
            throw new IllegalStateException("Required tool context value is missing: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Tool context value '" + key + "' must be " + type.getName()
                    + " but was " + value.getClass().getName());
        }
        return type.cast(value);
    }
}
