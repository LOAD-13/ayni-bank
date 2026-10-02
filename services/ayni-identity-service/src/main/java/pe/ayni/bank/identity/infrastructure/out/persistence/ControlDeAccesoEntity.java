package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.control_de_acceso}. */
@Entity
@Table(name = "control_de_acceso")
public class ControlDeAccesoEntity {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "fallos_consecutivos", nullable = false)
    private short fallosConsecutivos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ControlDeAccesoEntity() {
        // Exigido por JPA.
    }

    ControlDeAccesoEntity(UUID usuarioId, short fallosConsecutivos,
                          Instant bloqueadoHasta, Instant actualizadoEn) {
        this.usuarioId = usuarioId;
        this.fallosConsecutivos = fallosConsecutivos;
        this.bloqueadoHasta = bloqueadoHasta;
        this.actualizadoEn = actualizadoEn;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    short getFallosConsecutivos() {
        return fallosConsecutivos;
    }

    Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    @Override
    public String toString() {
        return "ControlDeAccesoEntity[usuarioId=" + usuarioId
                + ", fallos=" + fallosConsecutivos + "]";
    }
}
