package com.botwap.application.exception;

/**
 * Excepción base de errores de dominio/reglas de negocio.
 *
 * <p>Las subclases concretas (p. ej. entrada inválida) se añadirán en la Fase 4
 * junto con el motor conversacional.</p>
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}