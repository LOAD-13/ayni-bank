package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Un registro de la pista de auditoria tal como lo lee un auditor (HU-15): quien (actor), que
 * (tipo), sobre que (movimiento y recurso), desde donde (IP y agente), con que resultado y cuando.
 * Sin importes ni numeros de cuenta: el importe esta en los asientos del movimiento.
 */
public record EventoDeAuditoria(long id, String tipo, UUID usuarioId, UUID movimientoId, String motivo,
                                String resultado, String recurso, String ip, String agente,
                                Instant ocurridoEn) {
}
