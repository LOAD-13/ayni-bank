package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que un auditor puede pedir a la pista (AYNI-284). Todo es opcional; el rango es
 * {@code [desde, hasta)}.
 */
public record FiltroDeAuditoria(UUID usuarioId, String tipo, Instant desde, Instant hasta) {

    public FiltroDeAuditoria {
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            throw new IllegalArgumentException("El rango de fechas esta al reves.");
        }
    }
}
