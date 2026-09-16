package com.botwap.domain.engine;

import com.botwap.domain.engine.handler.CategoryMenuHandler;
import com.botwap.domain.engine.handler.ConfirmationMenuHandler;
import com.botwap.domain.engine.handler.DetailMenuHandler;
import com.botwap.domain.engine.handler.DocumentInputHandler;
import com.botwap.domain.engine.handler.IdentificationMenuHandler;
import com.botwap.domain.engine.handler.MainMenuHandler;
import com.botwap.domain.engine.handler.NameInputHandler;
import com.botwap.domain.engine.handler.ProductMenuHandler;
import com.botwap.domain.menu.InteractiveOption;
import com.botwap.domain.menu.MenuCatalog;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationState;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación de la máquina de estados: resuelve el {@link StateHandler} del
 * estado actual y delega la transición.
 *
 * <p>Los menús de categoría (Nivel 2) se registran a partir de
 * {@link ServiceBranch}, de modo que las cinco ramas comparten un único handler
 * parametrizado.</p>
 *
 * <p>Los estados terminales ({@code FINAL}, {@code CANCELLED}) no tienen handler:
 * el motor responde {@link EngineResult#silent(ConversationState)} —el bot
 * permanece en silencio— salvo que el usuario reinicie explícitamente
 * ({@code hola} / {@code menu}).</p>
 */
@Component
public final class ConversationEngineImpl implements ConversationEngine {

    private final Map<ConversationState, StateHandler> handlersByState;

    public ConversationEngineImpl() {
        this.handlersByState = buildHandlers();
    }

    private static Map<ConversationState, StateHandler> buildHandlers() {
        Map<ConversationState, StateHandler> map = new EnumMap<>(ConversationState.class);
        map.put(ConversationState.MAIN_MENU, new MainMenuHandler());
        for (ServiceBranch branch : ServiceBranch.values()) {
            if (branch != ServiceBranch.UNKNOWN) {
                map.put(ConversationState.valueOf(branch.stateKey()), new CategoryMenuHandler(branch));
            }
        }
        map.put(ConversationState.PRODUCT_MENU, new ProductMenuHandler());
        map.put(ConversationState.DETAIL_MENU, new DetailMenuHandler());
        map.put(ConversationState.NAME_INPUT, new NameInputHandler());
        map.put(ConversationState.IDENTIFICATION_MENU, new IdentificationMenuHandler());
        map.put(ConversationState.DOCUMENT_INPUT, new DocumentInputHandler());
        map.put(ConversationState.CONFIRMATION_MENU, new ConfirmationMenuHandler());
        return Map.copyOf(map);
    }

    @Override
    public EngineResult process(EngineRequest request, Conversation conversation) {
        ConversationState current = request.currentState();

        if (current == null) {
            return welcome(request);
        }

        StateHandler handler = handlersByState.get(current);
        if (handler == null || current.isTerminal()) {
            return handleTerminal(current, request.input());
        }

        StateHandler.Context ctx = new StateHandler.Context(
                request.input(),
                current,
                request.userName(),
                List.copyOf(request.selections()));

        StateHandler.Outcome outcome = handler.handle(ctx);

        return new EngineResult(outcome.responseText(), outcome.nextState(), outcome.selection(),
                outcome.options());
    }

    /** Primera interacción (sin conversación): bienvenida personalizada + menú. */
    private EngineResult welcome(EngineRequest request) {
        return EngineResult.menu(BotCopy.welcome(request.userName()), ConversationState.MAIN_MENU,
                null, InteractiveOption.listOf(MenuCatalog.mainMenu()));
    }

    /**
     * Estado terminal: la conversación permanece cerrada y el bot en silencio.
     * Un mensaje que no sea un reinicio explícito no genera respuesta alguna
     * (evita el bucle de mensajes automáticos tras la despedida).
     */
    private EngineResult handleTerminal(ConversationState current, String input) {
        if (InputNormalizer.isRestartCommand(InputNormalizer.normalize(input))) {
            return EngineResult.menu(BotCopy.backToMain(), ConversationState.MAIN_MENU, null,
                    InteractiveOption.listOf(MenuCatalog.mainMenu()));
        }
        return EngineResult.silent(current);
    }
}
