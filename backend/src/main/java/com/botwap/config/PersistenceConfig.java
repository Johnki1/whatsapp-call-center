package com.botwap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Configuración de transacciones reactivas.
 *
 * <p>Permite ejecutar secuencias de operaciones R2DBC en una única
 * transacción real (necesaria para que {@code SELECT ... FOR UPDATE} mantenga
 * el lock sobre la fila de la conversación durante toda la Fase A).</p>
 */
@Configuration
public class PersistenceConfig {

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}