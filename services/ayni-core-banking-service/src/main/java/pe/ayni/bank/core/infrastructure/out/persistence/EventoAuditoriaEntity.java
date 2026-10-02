package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code core.evento_auditoria}. Solo se inserta: la pista no se corrige. */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String tipo;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "movimiento_id")
    private UUID movimientoId;

    @Column(length = 40)
    private String motivo;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected EventoAuditoriaEntity() {
        // Exigido por JPA.
    }

    EventoAuditoriaEntity(String tipo, UUID usuarioId, UUID movimientoId, String motivo,
                          Instant ocurridoEn) {
        this.tipo = tipo;
        this.usuarioId = usuarioId;
        this.movimientoId = movimientoId;
        this.motivo = motivo;
        this.ocurridoEn = ocurridoEn;
    }

    String getTipo() {
        return tipo;
    }

    UUID getMovimientoId() {
        return movimientoId;
    }

    String getMotivo() {
        return motivo;
    }

    @Override
    public String toString() {
        return "EventoAuditoriaEntity[id=" + id + ", tipo=" + tipo + "]";
    }
}
