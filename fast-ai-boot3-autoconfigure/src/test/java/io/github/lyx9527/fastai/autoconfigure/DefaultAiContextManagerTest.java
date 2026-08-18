package io.github.lyx9527.fastai.autoconfigure;

import io.github.lyx9527.fastai.context.HeuristicAiTokenEstimator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAiContextManagerTest {

    @Test
    void compressesOldMessagesAndKeepsRecentMessages() {
        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
        String conversationKey = "conversation-key";
        String longText = "historical-message-".repeat(120);
        memory.add(conversationKey, List.of(
                new UserMessage(longText + "1"),
                new AssistantMessage(longText + "2"),
                new UserMessage(longText + "3"),
                new AssistantMessage(longText + "4"),
                new UserMessage("recent-user"),
                new AssistantMessage("recent-assistant")));

        FastAiProperties properties = new FastAiProperties();
        properties.getContext().setMaxContextTokens(1_024);
        properties.getContext().setReservedOutputTokens(0);
        properties.getContext().setCompressionThreshold(0.2);
        properties.getContext().setTargetOccupancy(0.4);
        properties.getContext().setPreserveRecentMessages(2);
        properties.getContext().setCompressionBatchTokens(512);
        properties.getContext().setSummaryMaxTokens(128);

        DefaultAiContextManager manager = new DefaultAiContextManager(memory,
                new HeuristicAiTokenEstimator(), (messages, maxTokens) -> "compressed-summary", properties);

        var usage = manager.prepare(conversationKey, "system", "current question");
        List<org.springframework.ai.chat.messages.Message> compressed = memory.get(conversationKey);

        assertThat(usage.compressed()).isTrue();
        assertThat(usage.summarizedMessages()).isEqualTo(4);
        assertThat(usage.estimatedPromptTokens()).isLessThan(usage.tokensBeforeCompression());
        assertThat(compressed).hasSize(3);
        assertThat(compressed.get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(compressed.get(0).getText()).startsWith(DefaultAiContextManager.SUMMARY_PREFIX);
        assertThat(compressed.get(1).getText()).isEqualTo("recent-user");
        assertThat(compressed.get(2).getText()).isEqualTo("recent-assistant");
    }
}
