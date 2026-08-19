package io.github.lyx9527.fastai.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 基于 APT 生成 Bean 的默认 Tool 注册表。
 */
public final class DefaultAiToolRegistry implements AiToolRegistry {

    /** DeepSeek/OpenAI Tool 名称允许使用的字符格式。 */
    private static final Pattern TOOL_NAME = Pattern.compile("^[a-zA-Z0-9_-]+$");
    /** 以 Tool 名称为键的不可变注册信息。 */
    private final Map<String, Registration> registrations;

    public DefaultAiToolRegistry(Collection<AiGeneratedTool> generatedTools) {
        this(generatedTools, new DefaultAiToolSecurityEvaluator());
    }

    public DefaultAiToolRegistry(Collection<AiGeneratedTool> generatedTools,
            AiToolSecurityEvaluator securityEvaluator) {
        Map<String, Registration> discovered = new LinkedHashMap<>();
        if (generatedTools != null) {
            for (AiGeneratedTool generatedTool : generatedTools) {
                ToolCallback rawCallback = generatedTool.toolCallback();
                String name = rawCallback.getToolDefinition().name();
                if (name == null || !TOOL_NAME.matcher(name).matches()) {
                    throw new IllegalStateException(
                            "非法 LLM Tool 名称：" + name + "，名称只能包含字母、数字、下划线或短横线");
                }
                ToolCallback callback = new SecuredAiToolCallback(rawCallback, generatedTool.security(),
                        securityEvaluator);
                String description = rawCallback.getToolDefinition().description();
                Registration previous = discovered.putIfAbsent(name,
                        new Registration(callback, new AiToolMetadata(name, description,
                                Set.copyOf(generatedTool.groups()), generatedTool.toolSet(),
                                generatedTool.toolSetDescription()), generatedTool.security()));
                if (previous != null) {
                    throw new IllegalStateException("Duplicate LLM tool name: " + name);
                }
            }
        }
        this.registrations = Collections.unmodifiableMap(discovered);
    }

    @Override
    public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups,
            boolean includeAllWhenUnspecified) {
        return resolve(toolNames, toolGroups, Set.of(), includeAllWhenUnspecified);
    }

    @Override
    public Collection<ToolCallback> resolve(Set<String> toolNames, Set<String> toolGroups, Set<String> toolSets,
            boolean includeAllWhenUnspecified) {
        Set<String> names = toolNames == null ? Set.of() : toolNames;
        Set<String> groups = toolGroups == null ? Set.of() : toolGroups;
        Set<String> sets = toolSets == null ? Set.of() : toolSets;
        if (names.isEmpty() && groups.isEmpty() && sets.isEmpty()) {
            return includeAllWhenUnspecified
                    ? this.registrations.values().stream().map(Registration::callback).toList()
                    : List.of();
        }

        List<String> missing = names.stream().filter(name -> !this.registrations.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unknown LLM tools: " + missing);
        }
        Set<String> knownSets = this.registrations.values().stream()
                .map(Registration::toolSet)
                .filter(set -> set != null && !set.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        List<String> missingSets = sets.stream().filter(set -> !knownSets.contains(set)).toList();
        if (!missingSets.isEmpty()) {
            throw new IllegalArgumentException("Unknown LLM tool sets: " + missingSets);
        }

        Set<ToolCallback> selected = new LinkedHashSet<>();
        names.forEach(name -> selected.add(this.registrations.get(name).callback()));
        if (!groups.isEmpty()) {
            this.registrations.values().stream()
                    .filter(registration -> registration.groups().stream().anyMatch(groups::contains))
                    .map(Registration::callback)
                    .forEach(selected::add);
        }
        if (!sets.isEmpty()) {
            this.registrations.values().stream()
                    .filter(registration -> sets.contains(registration.toolSet()))
                    .map(Registration::callback)
                    .forEach(selected::add);
        }
        return new ArrayList<>(selected);
    }

    @Override
    public Collection<String> names() {
        return this.registrations.keySet();
    }

    @Override
    public Collection<AiToolMetadata> metadata() {
        return this.registrations.values().stream().map(Registration::metadata).toList();
    }

    /**
     * 单个 Tool 的内部注册信息。
     *
     * @param callback 已添加安全包装的 ToolCallback
     * @param metadata Tool 语义路由元数据
     * @param security Tool 安全元数据
     */
    private record Registration(
            ToolCallback callback,
            AiToolMetadata metadata,
            AiToolSecurityMetadata security) {

        private Set<String> groups() {
            return this.metadata.groups();
        }

        private String toolSet() {
            return this.metadata.toolSet();
        }
    }
}
