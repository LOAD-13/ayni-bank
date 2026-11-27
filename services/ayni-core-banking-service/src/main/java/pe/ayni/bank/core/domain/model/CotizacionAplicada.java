package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lo que quedo registrado de la cotizacion al convertir (HU-12): la referencia oficial, el margen del
 * segmento y su codigo, y la fuente. Con el tipo aplicado, permite recalcular la operacion.
 */
public record CotizacionAplicada(BigDecimal referencia, BigDecimal spread, String segmento, String fuente) {

    public CotizacionAplicada {
        Objects.requireNonNull(referencia, "referencia");
        Objects.requireNonNull(spread, "spread");
        Objects.requireNonNull(segmento, "segmento");
        Objects.requireNonNull(fuente, "fuente");
    }
}
