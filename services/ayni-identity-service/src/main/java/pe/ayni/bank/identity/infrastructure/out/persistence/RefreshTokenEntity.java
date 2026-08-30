package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.refresh_token}. */
@Entity
@Table(name = "refresh_token")
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(name = "emitido_en", nullable = false)
    private Instant emitidoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "consumido_en")
    private Instant consumidoEn;

    @Column(name = "invalidado_en")
    private Instant invalidadoEn;

    protected RefreshTokenEntity() {
        // Exigido por JPA.
    }

    RefreshTokenEntity(UUID id, UUID familiaId, UUID usuarioId, String huella,
                       Instant emitidoEn, Instant expiraEn, Instant consumidoEn) {
        this.id = id;
        this.familiaId = familiaId;
        this.usuarioId = usuarioId;
        this.huella = huella;
        this.emitidoEn = emitidoEn;
        this.expiraEn = expiraEn;
        this.consumidoEn = consumidoEn;
    }

    UUID getId() {
        return id;
    }

    UUID getFamiliaId() {
        return familiaId;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getHuella() {
        return huella;
    }

    Instant getEmitidoEn() {
        return emitidoEn;
    }

    Instant getExpiraEn() {
        return expiraEn;
    }

    Instant getConsumidoEn() {
        return consumidoEn;
    }

    Instant getInvalidadoEn() {
        return invalidadoEn;
    }

    /** Sin la huella: identifica una sesion activa. */
    @Override
    public String toString() {
        return "RefreshTokenEntity[id=" + id + ", familia=" + familiaId + "]";
    }
}
