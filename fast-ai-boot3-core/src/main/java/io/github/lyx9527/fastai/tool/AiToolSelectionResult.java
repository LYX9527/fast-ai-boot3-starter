package io.github.lyx9527.fastai.tool;

import java.util.Set;

/**
 * LLM Tool 语义选择结果。
 *
 * @param toolNames 本轮对话需要注入的 Tool 名称
 * @param confidence 语义选择置信度，范围为 0 到 1
 */
public record AiToolSelectionResult(Set<String> toolNames, double confidence) {

    public AiToolSelectionResult {
        toolNames = toolNames == null ? Set.of() : Set.copyOf(toolNames);
        confidence = Double.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0;
    }

    /** 返回不需要 Tool 的选择结果。 */
    public static AiToolSelectionResult none() {
        return new AiToolSelectionResult(Set.of(), 0);
    }

    /** 当前结果是否包含至少一个可注入 Tool。 */
    public boolean hasSelection() {
        return !this.toolNames.isEmpty();
    }
}
