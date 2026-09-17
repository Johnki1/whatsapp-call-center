package com.botwap.application.service;

import com.botwap.BaseIntegrationTest;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.port.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InactivityIntegrationTest extends BaseIntegrationTest {
    @Autowired InactivityService inactivityService;
    @Autowired ConversationRepository conversations;
    @Autowired DatabaseClient database;

    @Test
    void twoCyclesQueueExactlyOneReminderAndPreserveOriginalPrompt() {
        String waId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Conversation conversation = conversations.insert(Conversation.newActive(waId, "Usuario")).block();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        database.sql("""
                UPDATE conversation SET last_inbound_at = :inbound,
                    last_bot_message_at = :bot, last_interaction_at = :bot,
                    last_prompt_payload = '{"text":"Menú original"}'
                WHERE id = :id
                """)
                .bind("id", conversation.id()).bind("inbound", now.minusMinutes(17))
                .bind("bot", now.minusMinutes(16)).then().block();

        inactivityService.processDue().block();
        inactivityService.processDue().block();

        Long count = database.sql("SELECT count(*) AS total FROM outbox_message WHERE conversation_id = :id")
                .bind("id", conversation.id()).map(row -> row.get("total", Long.class)).one().block();
        assertThat(count).isEqualTo(1L);
        String content = database.sql("SELECT content FROM message WHERE conversation_id = :id")
                .bind("id", conversation.id()).map(row -> row.get("content", String.class)).one().block();
        assertThat(content).isEqualTo(InactivityService.REMINDER);
        Conversation saved = conversations.findById(conversation.id()).block();
        assertThat(saved.reminderAt()).isNotNull();
        assertThat(saved.lastPromptPayload()).isEqualTo("{\"text\":\"Menú original\"}");
        assertThat(saved.awaitingReplyMessageId()).isNotNull();
    }
}
