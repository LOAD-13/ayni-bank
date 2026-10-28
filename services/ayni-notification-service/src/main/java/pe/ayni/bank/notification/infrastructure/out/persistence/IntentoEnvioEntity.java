package pe.ayni.bank.notification.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "intento_envio")
public class IntentoEnvioEntity {

    @Id
    private UUID id;

    @Column(name = "notificacion_id", nullable = false)
    private UUID notificacionId;

    @Column(nullable = false)
    private short numero;

    @Column(nullable = false)
    private String resultado;

    private String detalle;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected IntentoEnvioEntity() {
    }

    IntentoEnvioEntity(UUID id, UUID notificacionId, short numero, String resultado, String detalle,
                       Instant ocurridoEn) {
        this.id = id;
        this.notificacionId = notificacionId;
        this.numero = numero;
        this.resultado = resultado;
        this.detalle = detalle;
        this.ocurridoEn = ocurridoEn;
    }

    public UUID getNotificacionId() {
        return notificacionId;
    }

    public short getNumero() {
        return numero;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }
}
