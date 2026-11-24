package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Un tipo de cambio oficial ya guardado, con el momento en que Ayni lo obtuvo de la fuente (HU-12).
 * La fecha de {@code oficial} es la de publicacion; {@code obtenidoEn}, la de la consulta: un sabado
 * se consulta de nuevo y se sigue sirviendo el valor del viernes.
 */
public record TipoDeCambioConocido(TipoDeCambioOficial oficial, Instant obtenidoEn) {

    public TipoDeCambioConocido {
        Objects.requireNonNull(oficial, "oficial");
        Objects.requireNonNull(obtenidoEn, "obtenidoEn");
    }
}
