package io.github.lyx9527.fastai.intent;

import java.util.Map;

/**
 * 意图识别结果。
 *
 * @param intentCode 命中的意图编码
 * @param confidence 模型置信度，范围为 0 到 1
 * @param slots 提取出的业务槽位
 * @param fallback 是否为未知意图兜底结果
 */
public record AiIntentResult(String intentCode, double confidence, Map<String, Object> slots, boolean fallback) {

    public AiIntentResult {
        intentCode = intentCode == null || intentCode.isBlank() ? "unknown" : intentCode;
        confidence = Math.max(0, Math.min(1, confidence));
        slots = slots == null ? Map.of() : Map.copyOf(slots);
    }

    public static AiIntentResult unknown() {
        return new AiIntentResult("unknown", 0, Map.of(), true);
    }
}
