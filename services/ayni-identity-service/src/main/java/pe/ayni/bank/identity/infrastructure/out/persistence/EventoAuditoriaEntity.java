package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.evento_auditoria}. */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String tipo;

    /** Nulo cuando el correo no correspondia a ninguna cuenta. */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "agente_usuario", nullable = false, length = 255)
    private String agenteUsuario;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    protected EventoAuditoriaEntity() {
        // Exigido por JPA.
    }

    EventoAuditoriaEntity(String tipo, UUID usuarioId, String ip, String agenteUsuario,
                          Instant ocurridoEn) {
        this.tipo = tipo;
        this.usuarioId = usuarioId;
        this.ip = ip;
        this.agenteUsuario = agenteUsuario;
        this.ocurridoEn = ocurridoEn;
    }

    /** Sin la IP, que es dato personal: vive en la tabla, no en las trazas. */
    @Override
    public String toString() {
        return "EventoAuditoriaEntity[id=" + id + ", tipo=" + tipo + "]";
    }
}
