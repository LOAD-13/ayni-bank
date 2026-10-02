package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Dinero;

/**
 * Operaciones monetarias del titular: transferir (HU-07) y depositar en simulacion.
 *
 * <p>Las dos son idempotentes: repetir la peticion con la misma clave devuelve el mismo
 * comprobante y no mueve el dinero dos veces.
 */
public interface OperarCuentaUseCase {

    Comprobante transferir(UUID usuarioId, String numeroDestino, Dinero importe,
                           String concepto, UUID claveDeIdempotencia);

    Comprobante depositarSimulado(UUID usuarioId, Dinero importe, UUID claveDeIdempotencia);
}
