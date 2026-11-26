package pe.ayni.bank.core.domain.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Dinero;

/**
 * El paso de confirmar de HU-12: el cliente acepta el tipo que vio y el servidor, antes de mover
 * dinero, vuelve a cotizar y comprueba que sigue siendo ese.
 */
public interface ConfirmarConversionUseCase {

    /**
     * @param tipoMostrado soles por dolar que la pantalla le enseno al cliente
     * @param enCacheMostrado si la pantalla le aviso de que el tipo era el ultimo conocido
     * @param clave clave de idempotencia: un reintento devuelve la conversion ya hecha, aunque el tipo
     *              haya cambiado desde entonces
     * @throws pe.ayni.bank.core.domain.model.TipoDeCambioCambioException
     *         si el tipo vigente ya no es el mostrado, o ahora viene de cache y el cliente no lo sabia
     */
    Conversion confirmar(UUID usuarioId, Dinero importe, BigDecimal tipoMostrado, boolean enCacheMostrado,
                         UUID clave);
}
