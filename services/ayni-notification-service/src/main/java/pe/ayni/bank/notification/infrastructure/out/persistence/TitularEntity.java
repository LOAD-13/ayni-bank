package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "titular")
public class TitularEntity {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private String correo;

    @Column(name = "nombre_pila", nullable = false)
    private String nombrePila;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected TitularEntity() {
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getCorreo() {
        return correo;
    }

    public String getNombrePila() {
        return nombrePila;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
