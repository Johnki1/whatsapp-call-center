package com.botwap.domain.engine;

import com.botwap.domain.model.Conversation;
import reactor.core.publisher.Mono;

/**
 * Motor conversacional (máquina de estados).
 *
 * <p>Resuelve el {@code ConversationStateHandler} correspondiente al estado
 * actual de la conversación, valida la entrada y produce la transición
 * (respuesta, siguiente estado, selección).</p>
 *
 * <p><strong>Esqueleto declarado en FASE 2.</strong> La implementación completa
 * (handlers por estado, comandos globales, validaciones) es de FASE 4.</p>
 */
public interface ConversationEngine {

    Mono<EngineResult> process(EngineRequest request, Conversation conversation);
}