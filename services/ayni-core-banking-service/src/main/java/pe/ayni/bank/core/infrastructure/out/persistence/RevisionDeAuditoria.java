package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Una revision de Hibernate Envers: cuando cambio una entidad versionada y quien lo hizo (HU-15,
 * AYNI-279). Las versiones de {@code cuenta} y {@code tarjeta} viven en sus tablas {@code _aud}
 * y apuntan aqui. El actor es el usuario de la sesion ({@code X-Ayni-Usuario}); un proceso
 * programado o la siembra inicial no tienen actor.
 */
@Entity
@Table(name = "revision_auditoria")
@RevisionEntity(RevisionDeAuditoria.ActorDeLaRevision.class)
public class RevisionDeAuditoria {

    static final String CABECERA_USUARIO = "X-Ayni-Usuario";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    @RevisionTimestamp
    @Column(nullable = false)
    private Instant momento;

    private UUID actor;

    public Long getId() {
        return id;
    }

    public Instant getMomento() {
        return momento;
    }

    public UUID getActor() {
        return actor;
    }

    /** Pone en cada revision el usuario de la peticion en curso, si lo hay. */
    public static class ActorDeLaRevision implements RevisionListener {

        @Override
        public void newRevision(Object revision) {
            ((RevisionDeAuditoria) revision).actor = actorActual();
        }

        static UUID actorActual() {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
                return null;
            }
            String usuario = atributos.getRequest().getHeader(CABECERA_USUARIO);
            try {
                return usuario == null ? null : UUID.fromString(usuario);
            } catch (IllegalArgumentException e) {
                // El gateway siempre pone un UUID; algo distinto no se registra como actor.
                return null;
            }
        }
    }
}
