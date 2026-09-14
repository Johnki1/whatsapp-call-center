package com.botwap.domain.engine;

import com.botwap.domain.engine.handler.CategoryMenuHandler;
import com.botwap.domain.engine.handler.ConfirmationMenuHandler;
import com.botwap.domain.engine.handler.DetailMenuHandler;
import com.botwap.domain.engine.handler.DocumentInputHandler;
import com.botwap.domain.engine.handler.GenericCategoryMenuHandler;
import com.botwap.domain.engine.handler.IdentificationMenuHandler;
import com.botwap.domain.engine.handler.MainMenuHandler;
import com.botwap.domain.engine.handler.ProductMenuHandler;
import com.botwap.domain.model.Conversation;
import com.botwap.domain.model.ConversationSelection;
import com.botwap.domain.model.ConversationState;
import com.botwap.domain.menu.MenuCatalog;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public final class ConversationEngineImpl implements ConversationEngine {

    private final Map<ConversationState, StateHandler> handlersByState;

    public ConversationEngineImpl() {
        this.handlersByState = buildHandlers();
    }

    private static Map<ConversationState, StateHandler> buildHandlers() {
        Map<ConversationState, StateHandler> map = new EnumMap<>(ConversationState.class);
        map.put(ConversationState.MAIN_MENU, new MainMenuHandler());
        map.put(ConversationState.PURCHASE_MENU,
                new CategoryMenuHandler("MAIN_MENU", "PURCHASE_MENU", MenuCatalog.purchaseMenu(),
                        "¿Que deseas comprar?",
                        selected -> ConversationState.PRODUCT_MENU));
        map.put(ConversationState.RECHARGE_MENU,
                new GenericCategoryMenuHandler("MAIN_MENU", "RECHARGE_MENU", MenuCatalog.rechargeMenu(),
                        "¿Que deseas recargar?",
                        MenuCatalog.rechargeDetailMenu()));
        map.put(ConversationState.COMPLAINT_MENU,
                new GenericCategoryMenuHandler("MAIN_MENU", "COMPLAINT_MENU", MenuCatalog.complaintMenu(),
                        "¿Que deseas reportar?",
                        MenuCatalog.complaintDetailMenu()));
        map.put(ConversationState.PERSONAL_INFO_MENU,
                new GenericCategoryMenuHandler("MAIN_MENU", "PERSONAL_INFO_MENU", MenuCatalog.personalInfoMenu(),
                        "¿Que informacion deseas?",
                        MenuCatalog.personalInfoDetailMenu()));
        map.put(ConversationState.SUPPORT_MENU,
                new GenericCategoryMenuHandler("MAIN_MENU", "SUPPORT_MENU", MenuCatalog.supportMenu(),
                        "¿En que soporte necesitas ayuda?",
                        MenuCatalog.supportDetailMenu()));
        map.put(ConversationState.PRODUCT_MENU, new ProductMenuHandler());
        map.put(ConversationState.DETAIL_MENU, new DetailMenuHandler());
        map.put(ConversationState.IDENTIFICATION_MENU, new IdentificationMenuHandler());
        map.put(ConversationState.DOCUMENT_INPUT, new DocumentInputHandler());
        map.put(ConversationState.CONFIRMATION_MENU, new ConfirmationMenuHandler());
        return Map.copyOf(map);
    }

    @Override
    public EngineResult process(EngineRequest request, Conversation conversation) {
        ConversationState current = request.currentState();

        if (current == null) {
            return handleNoConversation(request.input());
        }

        StateHandler handler = handlersByState.get(current);
        if (handler == null) {
            return handleTerminal(current, request.input());
        }

        StateHandler.Context ctx = new StateHandler.Context(
                request.input(),
                current,
                List.copyOf(request.selections()));

        StateHandler.Outcome outcome = handler.handle(ctx);

        return new EngineResult(outcome.responseText(), outcome.nextState(), outcome.selection());
    }

    private EngineResult handleNoConversation(String input) {
        String normalized = InputNormalizer.normalize(input);
        if (InputNormalizer.isHello(normalized)) {
            return new EngineResult("Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatMain(),
                    ConversationState.MAIN_MENU, null);
        }
        return new EngineResult("Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatMain(),
                ConversationState.MAIN_MENU, null);
    }

    private EngineResult handleTerminal(ConversationState current, String input) {
        String normalized = InputNormalizer.normalize(input);
        if (InputNormalizer.isHello(normalized) || "menu".equals(normalized)) {
            return new EngineResult("Hola! Bienvenido.\n\nSelecciona una opcion:\n\n" + formatMain(),
                    ConversationState.MAIN_MENU, null);
        }
        if ("cancelar".equals(normalized)) {
            return new EngineResult("Tu conversacion ha sido cancelada. Escribe 'hola' para comenzar de nuevo.",
                    ConversationState.CANCELLED, null);
        }
        return new EngineResult("Conversacion finalizada. Escribe 'hola' para comenzar de nuevo.", current, null);
    }

    /** Formatea el menu principal para la bienvenida. */
    private static String formatMain() {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (var opt : MenuCatalog.mainMenu()) {
            sb.append(i).append(". ").append(opt.displayLabel()).append("\n");
            i++;
        }
        return sb.toString().trim();
    }
}