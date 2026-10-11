package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.confirmacion_de_operacion} (HU-07). */
@Entity
@Table(name = "confirmacion_de_operacion")
public class ConfirmacionDeOperacionEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(nullable = false, length = 30)
    private String metodo;

    @Column(name = "desafio_codigo_id")
    private UUID desafioCodigoId;

    @Column(name = "intentos_fallidos", nullable = false)
    private short intentosFallidos;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usada_en")
    private Instant usadaEn;

    protected ConfirmacionDeOperacionEntity() {
        // Exigido por JPA.
    }

    @SuppressWarnings("java:S107") // Una columna por parametro.
    ConfirmacionDeOperacionEntity(UUID id, UUID usuarioId, String huella, String metodo, UUID desafioCodigoId,
                                  short intentosFallidos, Instant creadaEn, Instant expiraEn, Instant usadaEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.huella = huella;
        this.metodo = metodo;
        this.desafioCodigoId = desafioCodigoId;
        this.intentosFallidos = intentosFallidos;
        this.creadaEn = creadaEn;
        this.expiraEn = expiraEn;
        this.usadaEn = usadaEn;
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

    String getMetodo() {
        return metodo;
    }

    UUID getDesafioCodigoId() {
        return desafioCodigoId;
    }

    short getIntentosFallidos() {
        return intentosFallidos;
    }

    Instant getCreadaEn() {
        return creadaEn;
    }

    Instant getExpiraEn() {
        return expiraEn;
    }

    Instant getUsadaEn() {
        return usadaEn;
    }
}
