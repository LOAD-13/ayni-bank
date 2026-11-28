package pe.ayni.bank.core.domain.model;

import java.util.List;

/** Una pagina de la pista, del evento mas reciente al mas antiguo, con el total para paginar. */
public record PaginaDeAuditoria(List<EventoDeAuditoria> eventos, int pagina, int tamano, long total) {
}
