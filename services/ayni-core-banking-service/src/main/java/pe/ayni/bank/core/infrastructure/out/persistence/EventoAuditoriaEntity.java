package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code core.evento_auditoria}. Solo se inserta: la pista no se corrige. */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String tipo;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "movimiento_id")
    private UUID movimientoId;

    @Column(length = 40)
    private String motivo;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    /** Desde donde se hizo; hoy solo lo lleva la revelacion del PAN (HU-09). */
    @Column(length = 45)
    private String ip;

    @Column(length = 120)
    private String recurso;

    @Column(length = 256)
    private String agente;

    @Column(nullable = false, length = 12)
    private String resultado;

    private static final Set<String> RECHAZOS =
            Set.of("OPERACION_RECHAZADA", "APERTURA_RECHAZADA", "ACCESO_AJENO_DENEGADO");

    protected EventoAuditoriaEntity() {
        // Exigido por JPA.
    }

    EventoAuditoriaEntity(String tipo, UUID usuarioId, UUID movimientoId, String motivo,
                          Instant ocurridoEn) {
        this.tipo = tipo;
        this.usuarioId = usuarioId;
        this.movimientoId = movimientoId;
        this.motivo = motivo;
        this.ocurridoEn = ocurridoEn;
        this.resultado = RECHAZOS.contains(tipo) ? "RECHAZADO" : "EXITO";
    }

    EventoAuditoriaEntity(String tipo, UUID usuarioId, UUID movimientoId, Instant ocurridoEn, String ip) {
        this(tipo, usuarioId, movimientoId, null, ocurridoEn);
        this.ip = ip;
    }

    /** IP, user-agent y recurso de la peticion; la IP que ya traia el evento se respeta. */
    EventoAuditoriaEntity conPeticion(String ip, String agente, String recurso) {
        if (this.ip == null) {
            this.ip = ip;
        }
        this.agente = agente;
        this.recurso = recurso;
        return this;
    }

    String getResultado() {
        return resultado;
    }

    String getTipo() {
        return tipo;
    }

    UUID getMovimientoId() {
        return movimientoId;
    }

    String getMotivo() {
        return motivo;
    }

    @Override
    public String toString() {
        return "EventoAuditoriaEntity[id=" + id + ", tipo=" + tipo + "]";
    }
}
