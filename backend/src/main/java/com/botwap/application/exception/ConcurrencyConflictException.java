package com.botwap.application.exception;

/**
 * Conflicto de concurrencia detectado por el optimistic locking de la
 * conversación (la versión esperada no coincide con la persistida).
 */
public class ConcurrencyConflictException extends DomainException {

    public ConcurrencyConflictException(java.util.UUID conversationId) {
        super("Conflicto de concurrencia modificando la conversación " + conversationId);
    }
}