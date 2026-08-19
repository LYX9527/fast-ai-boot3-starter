package io.github.lyx9527.fastai.tool;

import java.util.Set;

/**
 * 提供给语义路由模型的精简 Tool 元数据。
 *
 * @param name Tool 唯一名称
 * @param description Tool 业务能力说明
 * @param groups Tool 所属分组
 * @param toolSet Tool 所属工具集名称
 * @param toolSetDescription 工具集业务说明
 */
public record AiToolMetadata(
        String name,
        String description,
        Set<String> groups,
        String toolSet,
        String toolSetDescription) {

    public AiToolMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        description = description == null ? "" : description;
        groups = groups == null ? Set.of() : Set.copyOf(groups);
        toolSet = toolSet == null ? "" : toolSet;
        toolSetDescription = toolSetDescription == null ? "" : toolSetDescription;
    }
}
