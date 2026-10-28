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

    /** El motivo de una apertura rechazada. Hoy solo hay uno; la columna exige que conste. */
    static final String MOTIVO_CUENTA_DUPLICADA = "CUENTA_DUPLICADA";

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

    /** En una transaccion propia, por la misma razon que {@link #registrarRechazo}. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAperturaDuplicada(UUID usuarioId) {
        repositorio.save(new EventoAuditoriaEntity(TipoDeEventoDeOperacion.APERTURA_RECHAZADA.name(),
                usuarioId, null, MOTIVO_CUENTA_DUPLICADA, reloj.instant()));
    }

    /** En una transaccion propia: la consulta que lo detecta es de solo lectura. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAccesoAjeno(UUID usuarioId, UUID movimientoId) {
        repositorio.save(new EventoAuditoriaEntity(TipoDeEventoDeOperacion.ACCESO_AJENO_DENEGADO.name(),
                usuarioId, movimientoId, null, reloj.instant()));
    }
}
