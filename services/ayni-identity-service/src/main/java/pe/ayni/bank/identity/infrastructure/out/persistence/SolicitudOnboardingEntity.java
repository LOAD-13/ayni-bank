package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.solicitud_onboarding}. */
@Entity
@Table(name = "solicitud_onboarding")
public class SolicitudOnboardingEntity {

    @Id
    private UUID id;

    /**
     * Nulo en las solicitudes senuelo. Ver ADR-0008.
     *
     * <p>Se mapea como UUID suelto y no como {@code @ManyToOne} a proposito: una relacion
     * gestionada obligaria a que exista un usuario, que es justo lo que el senuelo no
     * tiene, y ademas arrastraria carga perezosa donde solo hace falta una clave.
     */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false, length = 32)
    private String estado;

    @Column(name = "paso_actual", nullable = false)
    private short pasoActual;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    protected SolicitudOnboardingEntity() {
        // Exigido por JPA.
    }

    SolicitudOnboardingEntity(UUID id, UUID usuarioId, String estado, short pasoActual,
                              Instant creadaEn, Instant actualizadaEn, Instant expiraEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.estado = estado;
        this.pasoActual = pasoActual;
        this.creadaEn = creadaEn;
        this.actualizadaEn = actualizadaEn;
        this.expiraEn = expiraEn;
    }

    UUID getId() {
        return id;
    }

    UUID getUsuarioId() {
        return usuarioId;
    }

    String getEstado() {
        return estado;
    }

    @Override
    public String toString() {
        return "SolicitudOnboardingEntity[id=" + id + ", estado=" + estado + "]";
    }
}
