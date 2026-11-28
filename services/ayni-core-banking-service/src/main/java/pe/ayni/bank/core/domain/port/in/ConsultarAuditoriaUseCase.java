package pe.ayni.bank.core.domain.port.in;

import java.time.LocalDate;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;

/**
 * Lectura de la pista de auditoria para el back-office (AYNI-284). Solo lee: la pista es de solo
 * insercion (V14) y este caso de uso no tiene un solo metodo que escriba.
 */
public interface ConsultarAuditoriaUseCase {

    /**
     * @param desde primer dia, hora de Lima, incluido; {@code null} si no se acota
     * @param hasta ultimo dia, hora de Lima, incluido; {@code null} si no se acota
     * @param pagina desde 0
     * @throws IllegalArgumentException si el rango esta al reves
     */
    PaginaDeAuditoria consultar(UUID usuarioId, String tipo, LocalDate desde, LocalDate hasta,
                                int pagina, int tamano);
}
