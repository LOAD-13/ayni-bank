package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code core.asiento}. */
@Entity
@Table(name = "asiento")
public class AsientoEntity {

    @Id
    private UUID id;

    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    /** Agrupa los asientos de una misma operacion. Su suma debe ser cero. */
    @Column(name = "movimiento_id", nullable = false)
    private UUID movimientoId;

    @Column(nullable = false, length = 16)
    private String tipo;

    /** BigDecimal contra NUMERIC. Ningun tipo de coma flotante toca un importe. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal importe;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Column(nullable = false, length = 140)
    private String concepto;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected AsientoEntity() {
        // Exigido por JPA.
    }

    AsientoEntity(UUID id, UUID cuentaId, UUID movimientoId, String tipo,
                  BigDecimal importe, String moneda, String concepto, Instant registradoEn) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.movimientoId = movimientoId;
        this.tipo = tipo;
        this.importe = importe;
        this.moneda = moneda;
        this.concepto = concepto;
        this.registradoEn = registradoEn;
    }

    UUID getId() {
        return id;
    }

    UUID getCuentaId() {
        return cuentaId;
    }

    UUID getMovimientoId() {
        return movimientoId;
    }

    String getTipo() {
        return tipo;
    }

    BigDecimal getImporte() {
        return importe;
    }

    String getMoneda() {
        return moneda;
    }

    String getConcepto() {
        return concepto;
    }

    Instant getRegistradoEn() {
        return registradoEn;
    }

    @Override
    public String toString() {
        return "AsientoEntity[id=" + id + ", tipo=" + tipo + "]";
    }
}
