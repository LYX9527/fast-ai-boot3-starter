package io.github.lyx9527.fastai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

/**
 * 在原始 ToolCallback 外增加执行前授权和执行结果审计的安全包装器。
 */
final class SecuredAiToolCallback implements ToolCallback {

    /** Tool 审计日志记录器。 */
    private static final Logger logger = LoggerFactory.getLogger(SecuredAiToolCallback.class);

    /** 原始 ToolCallback。 */
    private final ToolCallback delegate;
    /** 当前 Tool 的安全元数据。 */
    private final AiToolSecurityMetadata security;
    /** 执行前安全评估器。 */
    private final AiToolSecurityEvaluator evaluator;

    SecuredAiToolCallback(ToolCallback delegate, AiToolSecurityMetadata security,
            AiToolSecurityEvaluator evaluator) {
        this.delegate = delegate;
        this.security = security;
        this.evaluator = evaluator;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return this.delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        String toolName = getToolDefinition().name();
        try {
            this.evaluator.authorize(new AiToolAuthorizationRequest(toolName, this.security, toolContext));
        }
        catch (RuntimeException exception) {
            audit("denied", toolName, toolContext);
            throw exception;
        }
        try {
            String result = this.delegate.call(toolInput, toolContext);
            audit("succeeded", toolName, toolContext);
            return result;
        }
        catch (RuntimeException exception) {
            audit("failed", toolName, toolContext);
            throw exception;
        }
    }

    private void audit(String outcome, String toolName, @Nullable ToolContext context) {
        if (!this.security.auditEnabled()) {
            return;
        }
        Object tenantId = context == null ? null : context.getContext().get(AiToolContextValues.TENANT_ID);
        Object userId = context == null ? null : context.getContext().get(AiToolContextValues.USER_ID);
        logger.info("LLM tool audit outcome={} tool={} risk={} tenantId={} userId={}", outcome, toolName,
                this.security.risk(), tenantId, userId);
    }
}
