package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una fila de {@code tipo_cambio}: soles por dolar, de compra y de venta, de un dia y una fuente. */
@Entity
@Table(name = "tipo_cambio")
public class TipoCambioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "moneda_origen", nullable = false, length = 3)
    private String monedaOrigen;

    @Column(name = "moneda_destino", nullable = false, length = 3)
    private String monedaDestino;

    @Column(name = "referencia_compra", nullable = false)
    private BigDecimal referenciaCompra;

    @Column(name = "referencia_venta", nullable = false)
    private BigDecimal referenciaVenta;

    @Column(name = "fuente", nullable = false, length = 32)
    private String fuente;

    @Column(name = "obtenido_en", nullable = false)
    private Instant obtenidoEn;

    protected TipoCambioEntity() {
        // Para JPA. Las filas se escriben con el upsert del repositorio, no con save().
    }

    LocalDate getFecha() {
        return fecha;
    }

    BigDecimal getReferenciaCompra() {
        return referenciaCompra;
    }

    BigDecimal getReferenciaVenta() {
        return referenciaVenta;
    }

    String getFuente() {
        return fuente;
    }

    Instant getObtenidoEn() {
        return obtenidoEn;
    }
}
