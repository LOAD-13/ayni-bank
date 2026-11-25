package pe.ayni.bank.core.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.TipoDeCambioCambioException;
import pe.ayni.bank.core.domain.port.in.ConfirmarConversionUseCase;
import pe.ayni.bank.core.domain.port.in.ConvertirMonedaUseCase;
import pe.ayni.bank.core.domain.port.in.CotizarConversionUseCase;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;

/**
 * Confirmar una conversion (HU-12): volver a cotizar y convertir solo si el cliente vio el tipo que
 * se aplicaria. La comprobacion no es transaccional con la conversion a proposito: el tipo oficial
 * cambia una vez al dia, y lo que importa es que el cliente no acepte un numero y reciba otro.
 */
@Service
public class ConfirmarConversionService implements ConfirmarConversionUseCase {

    private final CotizarConversionUseCase cotizar;
    private final ConvertirMonedaUseCase convertir;
    private final RegistroDeIdempotenciaPort idempotencia;

    public ConfirmarConversionService(CotizarConversionUseCase cotizar, ConvertirMonedaUseCase convertir,
                                      RegistroDeIdempotenciaPort idempotencia) {
        this.cotizar = cotizar;
        this.convertir = convertir;
        this.idempotencia = idempotencia;
    }

    @Override
    public Conversion confirmar(UUID usuarioId, Dinero importe, BigDecimal tipoMostrado,
                                boolean enCacheMostrado, UUID clave) {
        Cotizacion cotizacion = cotizar.cotizar(usuarioId, importe.moneda());
        // Un reintento de una conversion ya hecha la devuelve tal cual: si el tipo se movio mientras
        // tanto, rechazarlo dejaria al cliente sin saber que su dinero ya se cambio, y a un paso de
        // repetirlo con otra clave.
        boolean reintento = idempotencia.resultadoDe(clave).isPresent();
        if (!reintento && cambio(cotizacion, tipoMostrado, enCacheMostrado)) {
            throw new TipoDeCambioCambioException(cotizacion, importe.moneda());
        }
        return convertir.convertir(usuarioId, importe, cotizacion, clave);
    }

    private static boolean cambio(Cotizacion cotizacion, BigDecimal tipoMostrado, boolean enCacheMostrado) {
        return cotizacion.tipo().solesPorDolar().compareTo(tipoMostrado) != 0
                || (cotizacion.tipo().enCache() && !enCacheMostrado);
    }
}
