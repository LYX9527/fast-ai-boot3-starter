package io.github.lyx9527.fastai.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.Set;

/**
 * APT 生成的 Spring Tool Adapter 统一实现契约。
 */
public interface AiGeneratedTool {

    /** 返回可由 Spring AI 调用的 ToolCallback。 */
    ToolCallback toolCallback();

    /** 返回 Tool 所属选择分组。 */
    default Set<String> groups() {
        return Set.of();
    }

    /** 返回 Tool 所属工具集名称。 */
    default String toolSet() {
        return "";
    }

    /** 返回工具集业务说明。 */
    default String toolSetDescription() {
        return "";
    }

    /** 返回 Tool 执行安全元数据。 */
    default AiToolSecurityMetadata security() {
        return AiToolSecurityMetadata.defaults();
    }
}
