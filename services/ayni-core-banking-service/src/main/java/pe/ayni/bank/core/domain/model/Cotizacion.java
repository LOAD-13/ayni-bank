package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Lo que se le ofrece al cliente antes de convertir (HU-12): el tipo que se le aplicara, el tipo de
 * referencia del que sale y el segmento cuyo margen se uso. La pantalla muestra los tres, y la
 * auditoria los guarda junto a la conversion.
 *
 * @param referencia soles por dolar oficial, el de compra o el de venta segun el sentido
 * @param fuente de donde salio la referencia oficial
 */
public record Cotizacion(TipoDeCambioAplicado tipo, BigDecimal referencia, SegmentoCambiario segmento,
                         String fuente) {

    public Cotizacion {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(referencia, "referencia");
        Objects.requireNonNull(segmento, "segmento");
        Objects.requireNonNull(fuente, "fuente");
    }

    /** Lo que se guarda junto a la conversion. */
    public CotizacionAplicada aplicada() {
        return new CotizacionAplicada(referencia, segmento.spread(), segmento.codigo(), fuente);
    }
}
