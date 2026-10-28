package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.token_recuperacion} (HU-21). */
@Entity
@Table(name = "token_recuperacion")
public class TokenDeRecuperacionEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(name = "emitido_en", nullable = false)
    private Instant emitidoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    protected TokenDeRecuperacionEntity() {
        // Exigido por JPA.
    }

    TokenDeRecuperacionEntity(UUID id, UUID usuarioId, String huella,
                              Instant emitidoEn, Instant expiraEn, Instant usadoEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.huella = huella;
        this.emitidoEn = emitidoEn;
        this.expiraEn = expiraEn;
        this.usadoEn = usadoEn;
    }

    UUID getId() {
        return id;
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

    Instant getUsadoEn() {
        return usadoEn;
    }

    Instant getAnuladoEn() {
        return anuladoEn;
    }
}
