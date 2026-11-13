package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Tipo de cambio de referencia que publica la fuente oficial para un dia (HU-12): soles por
 * dolar, de compra y de venta, sin el spread de Ayni. Cumple las mismas reglas que la tabla
 * {@code tipo_cambio}: los dos positivos y la venta nunca por debajo de la compra.
 */
public record TipoDeCambioOficial(LocalDate fecha, BigDecimal compra, BigDecimal venta, String fuente) {

    public TipoDeCambioOficial {
        Objects.requireNonNull(fecha, "fecha");
        Objects.requireNonNull(compra, "compra");
        Objects.requireNonNull(venta, "venta");
        Objects.requireNonNull(fuente, "fuente");
        if (compra.signum() <= 0 || venta.signum() <= 0) {
            throw new IllegalArgumentException("El tipo de cambio debe ser positivo");
        }
        if (venta.compareTo(compra) < 0) {
            throw new IllegalArgumentException("La venta no puede ser menor que la compra");
        }
    }
}
