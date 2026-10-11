package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notificacion")
public class NotificacionEntity {

    @Id
    private UUID id;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private UUID eventoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(nullable = false, updatable = false)
    private String plantilla;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private short intentos;

    @Column(name = "proximo_intento_en", nullable = false)
    private Instant proximoIntentoEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, String> datos;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "enviada_en")
    private Instant enviadaEn;

    protected NotificacionEntity() {
    }

    void actualizar(String estado, short intentos, Instant proximoIntentoEn, Instant enviadaEn) {
        this.estado = estado;
        this.intentos = intentos;
        this.proximoIntentoEn = proximoIntentoEn;
        this.enviadaEn = enviadaEn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getPlantilla() {
        return plantilla;
    }

    public String getEstado() {
        return estado;
    }

    public short getIntentos() {
        return intentos;
    }

    public Instant getProximoIntentoEn() {
        return proximoIntentoEn;
    }

    public Map<String, String> getDatos() {
        return datos;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }
}
