package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;

/**
 * Cambia dinero entre la cuenta en soles y la cuenta en dolares del mismo titular (HU-12). El
 * tipo aplicado llega ya calculado, con su spread: obtenerlo es tarea de la cotizacion.
 */
public interface ConvertirMonedaUseCase {

    /**
     * @param importe lo que sale, en la moneda de origen; la de destino es la otra
     * @param clave clave de idempotencia: un reintento devuelve la misma conversion
     */
    Conversion convertir(UUID usuarioId, Dinero importe, TipoDeCambioAplicado tipo, UUID clave);
}
