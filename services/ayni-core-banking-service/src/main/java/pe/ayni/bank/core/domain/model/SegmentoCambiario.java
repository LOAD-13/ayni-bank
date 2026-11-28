package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un segmento comercial de la politica cambiaria (HU-12, diseño §3.7.1): el margen total del banco
 * y el saldo promedio desde el que se aplica. Coincide con una fila de {@code segmento_cambiario}.
 *
 * @param spread margen total en tanto por uno: la mitad se aplica a la compra y la mitad a la venta
 */
public record SegmentoCambiario(String codigo, BigDecimal spread, BigDecimal saldoPromedioMinimo) {

    public SegmentoCambiario {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(spread, "spread");
        Objects.requireNonNull(saldoPromedioMinimo, "saldoPromedioMinimo");
        if (spread.signum() < 0 || spread.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("El spread debe estar entre 0 y 1");
        }
        if (saldoPromedioMinimo.signum() < 0) {
            throw new IllegalArgumentException("El saldo promedio minimo no puede ser negativo");
        }
    }
}
