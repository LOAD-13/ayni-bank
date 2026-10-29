package pe.ayni.bank.core.domain.model;

import java.time.Instant;

/** HU-09: el CVV de este momento y hasta cuando vale. No se guarda en ningun sitio. */
public record CvvDinamico(String cvv, Instant validoHasta) {
}
