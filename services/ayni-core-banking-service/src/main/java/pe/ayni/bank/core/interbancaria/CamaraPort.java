
package pe.ayni.bank.core.interbancaria;

import java.util.UUID;

/**
 * Puerto de salida para comunicarse con
 * la camara de compensacion interbancaria.
 */
public interface CamaraPort {

    EstadoInterbancario enviar(
        UUID id,
        String cci,
        String importe
    );

    EstadoInterbancario consultar(UUID id);
}
