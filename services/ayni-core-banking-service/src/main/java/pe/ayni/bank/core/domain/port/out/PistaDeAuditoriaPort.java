package pe.ayni.bank.core.domain.port.out;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;

/**
 * Pista de auditoria de las operaciones monetarias, como la de identity para los ingresos.
 *
 * <p>Registra tambien los rechazos, que no dejan asientos: sin ellos no se puede responder
 * cuantas veces intento un cliente transferir sin saldo o a una cuenta que no existe.
 */
public interface PistaDeAuditoriaPort {

    /** Operacion que movio dinero o que se respondio con su comprobante original. */
    void registrar(TipoDeEventoDeOperacion tipo, UUID usuarioId, UUID movimientoId);

    /**
     * Operacion rechazada. Debe quedar escrita aunque la transaccion de la operacion se
     * deshaga: la implementacion la confirma en una transaccion propia.
     */
    void registrarRechazo(UUID usuarioId, MotivoDeRechazo motivo);
}
