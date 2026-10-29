package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** La fila de la tarjeta (HU-09): el PAN solo aparece cifrado y como huella. */
@Entity
@Table(name = "tarjeta")
public class TarjetaEntity {

    @Id
    private UUID id;

    @Column(name = "cuenta_id", nullable = false)
    private UUID cuentaId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "pan_cifrado", nullable = false, length = 120)
    private String panCifrado;

    @Column(name = "pan_huella", nullable = false, length = 64)
    private String panHuella;

    @Column(nullable = false, length = 4)
    private String ultimos4;

    @Column(name = "vence_en", nullable = false)
    private LocalDate venceEn;

    @Column(nullable = false, length = 16)
    private String estado;

    @Column(name = "emitida_en", nullable = false)
    private Instant emitidaEn;

    protected TarjetaEntity() {
        // Para JPA.
    }

    @SuppressWarnings("java:S107") // Una columna por parametro.
    TarjetaEntity(UUID id, UUID cuentaId, UUID usuarioId, String panCifrado, String panHuella, String ultimos4,
                  LocalDate venceEn, String estado, Instant emitidaEn) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.usuarioId = usuarioId;
        this.panCifrado = panCifrado;
        this.panHuella = panHuella;
        this.ultimos4 = ultimos4;
        this.venceEn = venceEn;
        this.estado = estado;
        this.emitidaEn = emitidaEn;
    }

    UUID id() {
        return id;
    }

    UUID cuentaId() {
        return cuentaId;
    }

    UUID usuarioId() {
        return usuarioId;
    }

    String panCifrado() {
        return panCifrado;
    }

    LocalDate venceEn() {
        return venceEn;
    }

    String estado() {
        return estado;
    }

    Instant emitidaEn() {
        return emitidaEn;
    }
}
