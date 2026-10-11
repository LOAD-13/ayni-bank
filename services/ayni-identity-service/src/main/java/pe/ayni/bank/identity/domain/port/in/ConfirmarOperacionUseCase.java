package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.OperacionAConfirmar;
import pe.ayni.bank.identity.domain.model.ConfirmacionEmitida;
import pe.ayni.bank.identity.domain.model.ConfirmacionIniciada;

/** HU-07 · Confirmar una operacion con el segundo factor y obtener el token para core. ADR-0031. */
public interface ConfirmarOperacionUseCase {

    /**
     * Abre la confirmacion. Si el titular usa el correo como segundo factor, le envia el
     * codigo; si usa la app autenticadora, no hay nada que enviar.
     */
    ConfirmacionIniciada iniciar(UUID usuarioId, OperacionAConfirmar operacion, HuellaDeCliente cliente);

    /** Comprueba el codigo y, si es correcto, emite el token de confirmacion. */
    ConfirmacionEmitida verificar(UUID usuarioId, UUID confirmacionId, String codigo, HuellaDeCliente cliente);
}
