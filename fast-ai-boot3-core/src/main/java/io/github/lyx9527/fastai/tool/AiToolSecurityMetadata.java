package io.github.lyx9527.fastai.tool;

import java.util.Set;

/**
 * Tool 运行时安全元数据。
 *
 * @param risk 风险等级
 * @param permissions 执行所需的全部权限
 * @param requireConfirmation 是否要求显式确认
 * @param auditEnabled 是否启用审计日志
 */
public record AiToolSecurityMetadata(
        AiToolRiskLevel risk,
        Set<String> permissions,
        boolean requireConfirmation,
        boolean auditEnabled) {

    public AiToolSecurityMetadata {
        risk = risk == null ? AiToolRiskLevel.READ_ONLY : risk;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static AiToolSecurityMetadata defaults() {
        return new AiToolSecurityMetadata(AiToolRiskLevel.READ_ONLY, Set.of(), false, true);
    }

    public boolean confirmationRequired() {
        return this.requireConfirmation || this.risk == AiToolRiskLevel.DANGEROUS;
    }
}
