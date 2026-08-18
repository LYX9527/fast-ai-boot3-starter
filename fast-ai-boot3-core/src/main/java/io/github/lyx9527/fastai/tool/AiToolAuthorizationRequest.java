package io.github.lyx9527.fastai.tool;

import org.springframework.ai.chat.model.ToolContext;

/**
 * Tool 执行前的安全授权请求。
 *
 * @param toolName 待执行的 Tool 名称
 * @param security Tool 声明的安全元数据
 * @param toolContext 当前可信 ToolContext
 */
public record AiToolAuthorizationRequest(
        String toolName,
        AiToolSecurityMetadata security,
        ToolContext toolContext) {
}
