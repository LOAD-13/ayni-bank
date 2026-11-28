package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Moneda;

/**
 * Puerto de entrada de HU-05.
 *
 * <p>La cuenta en soles la abre el escuchador del evento {@code SolicitudAprobada}: es la
 * consecuencia de que un KYC salga aprobado. La cuenta en dolares la pide el propio cliente
 * ya con la de soles abierta (HU-12).
 */
public interface AbrirCuentaDeAhorroUseCase {

    /**
     * @param solicitudId identificador de la solicitud de onboarding aprobada. Sirve de
     *                    clave de idempotencia: un mismo evento entregado dos veces
     *                    —RabbitMQ garantiza «al menos una vez», no «exactamente una»—
     *                    tiene que abrir una sola cuenta.
     * @throws pe.ayni.bank.core.domain.model.CuentaDuplicadaException
     *         si el titular ya tiene una cuenta activa en esa moneda
     */
    Cuenta abrirPara(UUID usuarioId, UUID solicitudId, Moneda moneda);
}
