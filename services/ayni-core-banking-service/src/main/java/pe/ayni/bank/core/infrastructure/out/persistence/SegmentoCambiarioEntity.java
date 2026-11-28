package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una fila de {@code segmento_cambiario} (V1): datos de referencia, solo lectura. */
@Entity
@Table(name = "segmento_cambiario")
public class SegmentoCambiarioEntity {

    @Id
    private Short id;

    @Column(name = "codigo", nullable = false, length = 24)
    private String codigo;

    @Column(name = "spread", nullable = false)
    private BigDecimal spread;

    @Column(name = "saldo_promedio_minimo", nullable = false)
    private BigDecimal saldoPromedioMinimo;

    protected SegmentoCambiarioEntity() {
        // Para JPA.
    }

    String getCodigo() {
        return codigo;
    }

    BigDecimal getSpread() {
        return spread;
    }

    BigDecimal getSaldoPromedioMinimo() {
        return saldoPromedioMinimo;
    }
}
