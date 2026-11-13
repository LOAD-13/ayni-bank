package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversion")
public class ConversionEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "movimiento_salida_id", nullable = false)
    private UUID movimientoSalidaId;

    @Column(name = "movimiento_entrada_id", nullable = false)
    private UUID movimientoEntradaId;

    @Column(name = "moneda_origen", nullable = false, length = 3)
    private String monedaOrigen;

    @Column(name = "importe_origen", nullable = false)
    private BigDecimal importeOrigen;

    @Column(name = "moneda_destino", nullable = false, length = 3)
    private String monedaDestino;

    @Column(name = "importe_destino", nullable = false)
    private BigDecimal importeDestino;

    @Column(name = "tipo_aplicado", nullable = false)
    private BigDecimal tipoAplicado;

    @Column(name = "tipo_obtenido_en", nullable = false)
    private Instant tipoObtenidoEn;

    @Column(name = "tipo_en_cache", nullable = false)
    private boolean tipoEnCache;

    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    protected ConversionEntity() {
        // Para JPA.
    }

    ConversionEntity(UUID id, UUID usuarioId, UUID movimientoSalidaId, UUID movimientoEntradaId,
                     String monedaOrigen, BigDecimal importeOrigen, String monedaDestino,
                     BigDecimal importeDestino, BigDecimal tipoAplicado, Instant tipoObtenidoEn,
                     boolean tipoEnCache, Instant registradaEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.movimientoSalidaId = movimientoSalidaId;
        this.movimientoEntradaId = movimientoEntradaId;
        this.monedaOrigen = monedaOrigen;
        this.importeOrigen = importeOrigen;
        this.monedaDestino = monedaDestino;
        this.importeDestino = importeDestino;
        this.tipoAplicado = tipoAplicado;
        this.tipoObtenidoEn = tipoObtenidoEn;
        this.tipoEnCache = tipoEnCache;
        this.registradaEn = registradaEn;
    }

    UUID getId() {
        return id;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    UUID getMovimientoSalidaId() {
        return movimientoSalidaId;
    }

    UUID getMovimientoEntradaId() {
        return movimientoEntradaId;
    }

    BigDecimal getTipoAplicado() {
        return tipoAplicado;
    }

    Instant getTipoObtenidoEn() {
        return tipoObtenidoEn;
    }

    boolean isTipoEnCache() {
        return tipoEnCache;
    }

    Instant getRegistradaEn() {
        return registradaEn;
    }
}
