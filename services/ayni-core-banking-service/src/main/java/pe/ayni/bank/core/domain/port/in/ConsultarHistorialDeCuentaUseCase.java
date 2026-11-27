package pe.ayni.bank.core.domain.port.in;

import java.time.Instant;

import pe.ayni.bank.core.domain.model.HistorialDeCuenta;

/** El estado de una cuenta en una fecha pasada y quien hizo cada cambio (HU-15, AYNI-280). */
public interface ConsultarHistorialDeCuentaUseCase {

    /**
     * @param fecha momento consultado
     * @throws java.util.NoSuchElementException si no hay una cuenta de cliente con ese numero
     */
    HistorialDeCuenta consultar(String numeroDeCuenta, Instant fecha);
}
