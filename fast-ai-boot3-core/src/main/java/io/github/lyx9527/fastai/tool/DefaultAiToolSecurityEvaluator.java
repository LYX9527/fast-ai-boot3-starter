package io.github.lyx9527.fastai.tool;

import org.springframework.ai.chat.model.ToolContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 默认 Tool 安全评估器，校验权限集合和具体 Tool 确认结果。
 */
public final class DefaultAiToolSecurityEvaluator implements AiToolSecurityEvaluator {

    @Override
    public void authorize(AiToolAuthorizationRequest request) {
        AiToolSecurityMetadata security = request.security();
        if (security.permissions().isEmpty() && !security.confirmationRequired()) {
            return;
        }
        ToolContext context = request.toolContext();
        if (context == null) {
            throw new AiToolSecurityException("Tool context is required to authorize tool: " + request.toolName());
        }

        Set<String> grantedPermissions = stringSet(context.getContext().get(AiToolContextValues.PERMISSIONS));
        if (!grantedPermissions.containsAll(security.permissions())) {
            Set<String> missing = new HashSet<>(security.permissions());
            missing.removeAll(grantedPermissions);
            throw new AiToolSecurityException("Missing permissions " + missing + " for tool: " + request.toolName());
        }

        if (security.confirmationRequired()) {
            Set<String> confirmedTools = stringSet(context.getContext().get(AiToolContextValues.CONFIRMED_TOOLS));
            if (!confirmedTools.contains(request.toolName())) {
                throw new AiToolSecurityException("Explicit confirmation is required for tool: "
                        + request.toolName());
            }
        }
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        collection.stream().filter(item -> item != null).map(Object::toString).forEach(result::add);
        return result;
    }
}
