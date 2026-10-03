package pe.ayni.bank.core.infrastructure.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;

/** Implementa {@link PistaDeAuditoriaPort} sobre JPA. */
@Repository
public class AdaptadorPistaDeAuditoria implements PistaDeAuditoriaPort {

    private final EventoAuditoriaJpaRepository repositorio;
    private final Clock reloj;

    AdaptadorPistaDeAuditoria(EventoAuditoriaJpaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    /** En la transaccion de la operacion: si el movimiento se deshace, su evento tambien. */
    @Override
    public void registrar(TipoDeEventoDeOperacion tipo, UUID usuarioId, UUID movimientoId) {
        repositorio.save(new EventoAuditoriaEntity(tipo.name(), usuarioId, movimientoId, null,
                reloj.instant()));
    }

    /** En una transaccion propia: el rechazo deshace la de la operacion, pero no su rastro. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarRechazo(UUID usuarioId, MotivoDeRechazo motivo) {
        repositorio.save(new EventoAuditoriaEntity(TipoDeEventoDeOperacion.OPERACION_RECHAZADA.name(),
                usuarioId, null, motivo.name(), reloj.instant()));
    }
}
