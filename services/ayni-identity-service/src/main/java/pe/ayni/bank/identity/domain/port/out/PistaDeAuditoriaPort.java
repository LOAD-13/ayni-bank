package pe.ayni.bank.identity.domain.port.out;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.TipoDeEventoDeAcceso;

/**
 * Pista de auditoria. HU-04 exige que todo ingreso, exitoso o fallido, quede registrado
 * con IP y agente de usuario.
 *
 * <p>Es una tabla y no un log de aplicacion: un log rota, se trunca y lo lee cualquiera con
 * acceso a Loki. La pista tiene finalidad, retencion y control de acceso propios, que es lo
 * que la Ley N.o 29733 espera de un registro que contiene direcciones IP.
 */
public interface PistaDeAuditoriaPort {

    /** @param usuarioId nulo cuando el correo no corresponde a ninguna cuenta */
    void registrar(TipoDeEventoDeAcceso tipo, UUID usuarioId, HuellaDeCliente cliente);
}
