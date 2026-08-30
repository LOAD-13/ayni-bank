package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.desafio_segundo_factor}. */
@Entity
@Table(name = "desafio_segundo_factor")
public class DesafioEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "consumido_en")
    private Instant consumidoEn;

    protected DesafioEntity() {
        // Exigido por JPA.
    }

    DesafioEntity(UUID id, UUID usuarioId, Instant creadoEn, Instant expiraEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    void consumir(Instant momento) {
        this.consumidoEn = momento;
    }

    UUID getId() {
        return id;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    Instant getExpiraEn() {
        return expiraEn;
    }

    Instant getConsumidoEn() {
        return consumidoEn;
    }

    @Override
    public String toString() {
        return "DesafioEntity[id=" + id + "]";
    }
}
