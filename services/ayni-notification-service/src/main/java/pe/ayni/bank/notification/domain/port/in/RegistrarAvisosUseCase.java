package pe.ayni.bank.notification.domain.port.in;

import java.util.List;

import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;

/**
 * Deja pendientes los avisos de un evento. Si el evento ya se registro, no crea nada:
 * es lo que garantiza un solo correo aunque el mensaje llegue dos veces.
 */
public interface RegistrarAvisosUseCase {

    /** @return cuantos avisos eran nuevos */
    int registrar(List<AvisoDeMovimiento> avisos);
}
