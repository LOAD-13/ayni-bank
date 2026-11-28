package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * El tipo de cambio oficial con el que se cotiza hoy (HU-12): lo publicado por la fuente, cuando
 * Ayni lo obtuvo y si es el ultimo valor conocido porque la fuente no respondio. Todavia sin el
 * margen del banco.
 */
public record TipoDeCambioVigente(TipoDeCambioOficial oficial, Instant obtenidoEn, boolean enCache) {

    public TipoDeCambioVigente {
        Objects.requireNonNull(oficial, "oficial");
        Objects.requireNonNull(obtenidoEn, "obtenidoEn");
    }
}
