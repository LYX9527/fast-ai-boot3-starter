package io.github.lyx9527.fastai.intent;

import java.util.Set;

/**
 * 可供意图识别服务匹配的业务意图定义。
 *
 * @param code 意图唯一编码
 * @param description 意图业务说明
 * @param examples 示例语句集合
 * @param requiredSlots 必需槽位名称集合
 */
public record AiIntentDefinition(String code, String description, Set<String> examples, Set<String> requiredSlots) {

    public AiIntentDefinition {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("intent code must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("intent description must not be blank");
        }
        examples = examples == null ? Set.of() : Set.copyOf(examples);
        requiredSlots = requiredSlots == null ? Set.of() : Set.copyOf(requiredSlots);
    }
}
