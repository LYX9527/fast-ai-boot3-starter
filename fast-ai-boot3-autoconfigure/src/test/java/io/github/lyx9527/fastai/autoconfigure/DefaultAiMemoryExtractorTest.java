package io.github.lyx9527.fastai.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAiMemoryExtractorTest {

    private final DefaultAiMemoryExtractor extractor = new DefaultAiMemoryExtractor();

    @Test
    void extractsExplicitAndDurableChineseMemories() {
        assertThat(this.extractor.extract(
                "请记住：以后默认使用中文回答。我喜欢无糖咖啡。我对花生过敏。我的职业是软件工程师。",
                "好的，我已经记住了。"))
                .containsExactly(
                        "以后默认使用中文回答",
                        "我喜欢无糖咖啡",
                        "我对花生过敏",
                        "我的职业是软件工程师");
    }

    @Test
    void extractsDurableEnglishMemories() {
        assertThat(this.extractor.extract(
                "I prefer concise answers. My preferred language is Chinese.",
                "Understood."))
                .containsExactly("I prefer concise answers", "My preferred language is Chinese");
    }

    @Test
    void ignoresOrdinaryRequestsAndAssistantInferences() {
        assertThat(this.extractor.extract(
                "帮我查询上海天气，再查一下订单 A1002。",
                "上海是晴天，我猜你喜欢晴天。"))
                .isEmpty();
    }

    @Test
    void ignoresSensitiveTransientAndOptOutContent() {
        assertThat(this.extractor.extract(
                "请记住我的 API Key 是 sk-test12345678。"
                        + "请记住我明天下午三点开会。"
                        + "不要记住我喜欢咖啡。",
                "好的。"))
                .isEmpty();
    }
}
