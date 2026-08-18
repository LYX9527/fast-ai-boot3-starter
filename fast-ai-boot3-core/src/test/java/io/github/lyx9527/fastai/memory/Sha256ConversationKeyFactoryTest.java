package io.github.lyx9527.fastai.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Sha256ConversationKeyFactoryTest {

    private final Sha256ConversationKeyFactory factory = new Sha256ConversationKeyFactory();

    @Test
    void createsStableUserScopedKeys() {
        AiMemoryScope first = new AiMemoryScope("tenant-a", "user-a", "conversation-a");
        AiMemoryScope otherUser = new AiMemoryScope("tenant-a", "user-b", "conversation-a");

        assertEquals(this.factory.create(first), this.factory.create(first));
        assertNotEquals(this.factory.create(first), this.factory.create(otherUser));
    }
}
