package com.botwap.application.service;

import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReengagementDomainTest {

    private static final Duration DELAY = Duration.ofMinutes(30);

    private Conversation conversationWithPrompt() {
        Instant now = Instant.now();
        return new Conversation(UUID.randomUUID(), "573001111111", ConversationState.PRODUCT_MENU,
                com.botwap.domain.model.ConversationStatus.ACTIVE, "Usuario", 1L,
                now.minus(Duration.ofHours(1)), now.minus(Duration.ofMinutes(31)), null,
                now.minus(Duration.ofMinutes(31)), now.minus(Duration.ofMinutes(31)),
                now.minus(Duration.ofMinutes(16)), now.minus(Duration.ofMinutes(16)), false,
                "{\"text\":\"Paquete\",\"interactive\":{\"type\":\"button\",\"body\":{\"text\":\"Paquete\"},"
                        + "\"action\":{\"buttons\":[{\"type\":\"reply\",\"reply\":{\"id\":\"5GB\",\"title\":\"5 GB\"}}]}}}",
                UUID.randomUUID());
    }

    @Test
    void beforeDelayNoReengagement() {
        Conversation conversation = conversationWithPrompt();
        Instant before = conversation.lastBotMessageAt().plus(DELAY).minusSeconds(1);
        assertThat(conversation.requiresReengagement(before, DELAY)).isFalse();
    }

    @Test
    void exactlyAtDelayRequiresReengagement() {
        Conversation conversation = conversationWithPrompt();
        Instant at = conversation.lastBotMessageAt().plus(DELAY);
        assertThat(conversation.requiresReengagement(at, DELAY)).isTrue();
    }

    @Test
    void terminalOrClosedNeverRequireReengagement() {
        Conversation closed = conversationWithPrompt().withStatus(
                com.botwap.domain.model.ConversationStatus.CLOSED);
        Conversation terminal = conversationWithPrompt()
                .withState(ConversationState.FINAL);
        Instant farFuture = Instant.now().plus(Duration.ofDays(1));
        assertThat(closed.requiresReengagement(farFuture, DELAY)).isFalse();
        assertThat(terminal.requiresReengagement(farFuture, DELAY)).isFalse();
    }

    @Test
    void pendingFlagRequiresReengagementImmediately() {
        Conversation pending = conversationWithPrompt().awaitingReply(
                null, conversationWithPrompt().lastPromptPayload(), true, null);
        assertThat(pending.requiresReengagement(Instant.now(), DELAY)).isTrue();
        assertThat(pending.lastPromptPayload()).isNotNull();
    }

    @Test
    void inboundClearsAwaitingStateButKeepsSuspendedMenu() {
        Conversation conversation = conversationWithPrompt();
        Conversation received = conversation.receivedAt(Instant.now());
        assertThat(received.awaitingReplyMessageId()).isNull();
        assertThat(received.lastBotMessageAt()).isNull();
        assertThat(received.reminderAt()).isNull();
        assertThat(received.lastPromptPayload()).isEqualTo(conversation.lastPromptPayload());
    }

    @Test
    void restoreRebuildsOriginalMenuButtons() {
        OutboxPayloadBuilder builder = new OutboxPayloadBuilder(new ObjectMapper());
        var restored = builder.restore(conversationWithPrompt().lastPromptPayload(),
                ConversationState.PRODUCT_MENU);
        assertThat(restored.responseText()).isEqualTo("Paquete");
        assertThat(restored.hasMenu()).isTrue();
        assertThat(restored.options()).hasSize(1);
        assertThat(restored.options().get(0).id()).isEqualTo("5GB");
        assertThat(restored.options().get(0).title()).isEqualTo("5 GB");
    }
}
