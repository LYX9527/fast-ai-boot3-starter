package io.github.lyx9527.fastai.context;

/**
 * Provider 无关的启发式 Token 估算器。
 * CJK 字符按单字符计数，其他可见字符使用约四字符一个 Token 的近似算法。
 */
public final class HeuristicAiTokenEstimator implements AiTokenEstimator {

    @Override
    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjk = 0;
        int other = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            if (isCjk(codePoint)) {
                cjk++;
            }
            else {
                other++;
            }
        }
        return cjk + (int) Math.ceil(other / 4.0);
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
