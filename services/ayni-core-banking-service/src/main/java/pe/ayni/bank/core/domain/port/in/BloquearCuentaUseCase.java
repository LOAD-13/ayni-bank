package pe.ayni.bank.core.domain.port.in;

import java.util.List;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;

/**
 * Bloqueo de la cuenta de un cliente con el principio de cuatro ojos (HU-15, AYNI-282): un operador
 * lo pide y otra persona lo aprueba. Los permisos los comprueba la entrada; aqui se hace cumplir
 * que no sea la misma persona.
 */
public interface BloquearCuentaUseCase {

    /**
     * @throws java.util.NoSuchElementException si no existe una cuenta de cliente con ese numero
     * @throws pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException si la cuenta no
     *         esta activa o ya tiene una solicitud pendiente
     */
    SolicitudDeBloqueo iniciar(UUID operador, String numeroDeCuenta, String motivo);

    /**
     * @throws java.util.NoSuchElementException si la solicitud no existe
     * @throws pe.ayni.bank.core.domain.model.CuatroOjosException si quien aprueba es quien la inicio
     */
    SolicitudDeBloqueo aprobar(UUID supervisor, UUID solicitudId);

    List<SolicitudDeBloqueo> pendientes();
}
