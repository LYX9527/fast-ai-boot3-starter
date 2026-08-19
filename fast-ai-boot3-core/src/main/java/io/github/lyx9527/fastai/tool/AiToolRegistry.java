package io.github.lyx9527.fastai.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;
import java.util.Set;

/**
 * 已注册 Tool 的查询和请求级选择接口。
 */
public interface AiToolRegistry {

    /** 按名称和分组解析本次请求可见的 Tool。 */
    Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups, boolean includeAllWhenUnspecified);

    /** 按名称、分组和工具集解析本次请求可见的 Tool。 */
    default Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups, Set<String> toolSets,
            boolean includeAllWhenUnspecified) {
        if (toolSets != null && !toolSets.isEmpty()) {
            throw new UnsupportedOperationException("This AiToolRegistry implementation does not support tool sets");
        }
        return resolve(toolNames, toolGroups, includeAllWhenUnspecified);
    }

    /** 返回全部已注册 Tool 名称。 */
    Collection<String> names();

    /**
     * 返回供 LLM 语义路由使用的精简 Tool 目录。
     *
     * <p>自定义注册表没有额外元数据时，默认仅暴露 Tool 名称。</p>
     */
    default Collection<AiToolMetadata> metadata() {
        return names().stream()
                .map(name -> new AiToolMetadata(name, "", Set.of(), "", ""))
                .toList();
    }
}
