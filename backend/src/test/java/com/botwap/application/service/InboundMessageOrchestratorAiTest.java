package com.botwap.application.service;

import com.botwap.domain.engine.ConversationEngineImpl;
import com.botwap.domain.engine.EngineResult;
import com.botwap.domain.engine.BotCopy;
import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.port.AiAssistant.AiReply;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class InboundMessageOrchestratorAiTest {

    @Test
    void supportIntentOpensSupportMenuAndRecordsBranch() {
        var orchestrator = new InboundMessageOrchestrator(new ConversationEngineImpl(),
                null, null, null, null, null, null, null);
        var conversation = Conversation.newActive("test-user", "Usuario");
        var fallback = EngineResult.menu(BotCopy.notUnderstood(), ConversationState.MAIN_MENU,
                null, InteractiveOption.listOf(MenuCatalog.mainMenu()));

        EngineResult result = ReflectionTestUtils.invokeMethod(orchestrator, "toAiResult",
                conversation, new AiReply("Te ayudo con tus datos móviles.", "SUPPORT"), fallback,
                java.util.List.of());

        assertThat(result).isNotNull();
        assertThat(result.nextState()).isEqualTo(ConversationState.SUPPORT_MENU);
        assertThat(result.options()).isEqualTo(InteractiveOption.listOf(MenuCatalog.supportMenu()));
        assertThat(result.selection()).isNotNull();
        assertThat(result.selection().optionKey()).isEqualTo("SUPPORT");
        assertThat(result.selection().level()).isEqualTo(1);
    }
}
