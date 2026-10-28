package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeSolicitud;

/**
 * Puerto de entrada de HU-05, escenario 3: un operador rechaza una solicitud tras revisarla.
 *
 * <p>El usuario no se activa ni se bloquea: la solicitud queda RECHAZADA, que es definitivo,
 * y se le avisa del motivo por correo.
 */
public interface RechazarSolicitudUseCase {

    /**
     * @throws pe.ayni.bank.identity.domain.model.SolicitudNoRechazableException si no existe,
     *         es un senuelo o ya esta cerrada
     */
    void rechazar(UUID solicitudId, MotivoDeRechazoDeSolicitud motivo);
}
