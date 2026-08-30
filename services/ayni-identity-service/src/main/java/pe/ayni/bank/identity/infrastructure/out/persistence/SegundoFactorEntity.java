package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.segundo_factor}. */
@Entity
@Table(name = "segundo_factor")
public class SegundoFactorEntity {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    /** Criptograma AES-256-GCM del secreto Base32. */
    @Column(nullable = false, length = 255)
    private String secreto;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    protected SegundoFactorEntity() {
        // Exigido por JPA.
    }

    SegundoFactorEntity(UUID usuarioId, String secreto, Instant creadoEn,
                        Instant confirmadoEn) {
        this.usuarioId = usuarioId;
        this.secreto = secreto;
        this.creadoEn = creadoEn;
        this.confirmadoEn = confirmadoEn;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getSecreto() {
        return secreto;
    }

    Instant getCreadoEn() {
        return creadoEn;
    }

    Instant getConfirmadoEn() {
        return confirmadoEn;
    }

    /** Sin el secreto, ni siquiera cifrado. */
    @Override
    public String toString() {
        return "SegundoFactorEntity[usuarioId=" + usuarioId
                + ", confirmado=" + (confirmadoEn != null) + "]";
    }
}
