package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/** Implementa {@link RepositorioDeSolicitudesPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeSolicitudes implements RepositorioDeSolicitudesPort {

    /**
     * Un expediente de onboarding sin terminar caduca a los siete dias. Sin caducidad, la
     * tabla acumula indefinidamente datos personales de gente que nunca completo el alta,
     * lo que contradice el principio de minimizacion de la Ley N.o 29733.
     */
    private static final Duration VIGENCIA = Duration.ofDays(7);

    private static final String ESTADO_INICIAL = "INICIADA";
    private static final short PRIMER_PASO = 1;

    private final SolicitudJpaRepository repositorio;
    private final Clock reloj;

    AdaptadorRepositorioDeSolicitudes(SolicitudJpaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public UUID abrirPara(UUID usuarioId) {
        return crear(usuarioId);
    }

    @Override
    public UUID abrirSenuelo() {
        // Se persiste de verdad, con usuario_id nulo. Devolver un UUID inventado sin
        // escribir nada haria que la siguiente peticion sobre esa solicitud respondiera
        // «no existe», y ahi se acabaria la indistinguibilidad. Ver ADR-0008.
        return crear(null);
    }

    private UUID crear(UUID usuarioId) {
        Instant ahora = reloj.instant();
        SolicitudOnboardingEntity solicitud = new SolicitudOnboardingEntity(
                UUID.randomUUID(), usuarioId, ESTADO_INICIAL, PRIMER_PASO,
                ahora, ahora, ahora.plus(VIGENCIA));

        return repositorio.save(solicitud).getId();
    }
}
