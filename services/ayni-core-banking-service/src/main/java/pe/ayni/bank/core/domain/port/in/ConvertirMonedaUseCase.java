package pe.ayni.bank.core.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Dinero;

/**
 * Cambia dinero entre la cuenta en soles y la cuenta en dolares del mismo titular (HU-12). La
 * cotizacion llega ya calculada, con su referencia, spread y segmento: obtenerla es tarea de
 * {@link CotizarConversionUseCase}.
 */
public interface ConvertirMonedaUseCase {

    /**
     * @param importe lo que sale, en la moneda de origen; la de destino es la otra
     * @param clave clave de idempotencia: un reintento devuelve la misma conversion
     */
    Conversion convertir(UUID usuarioId, Dinero importe, Cotizacion cotizacion, UUID clave);
}
