package pe.ayni.bank.core.application.usecase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.port.in.CotizarConversionUseCase;
import pe.ayni.bank.core.domain.port.in.ObtenerTipoDeCambioUseCase;
import pe.ayni.bank.core.domain.port.out.SegmentosCambiariosPort;
import pe.ayni.bank.core.domain.service.PoliticaDeSpread;

/**
 * Cotizacion de HU-12: el tipo oficial de hoy con el margen del segmento del cliente.
 *
 * <p>El segmento sale del saldo promedio de los ultimos 30 dias cerrados, de ayer hacia atras. Se
 * usa una ventana movil y no el mes calendario para que el primer dia de cada mes un cliente
 * preferencial no pase una jornada con la tarifa estandar solo porque aun no hay datos del mes.
 */
@Service
public class CotizarConversionService implements CotizarConversionUseCase {

    static final int DIAS_DE_PROMEDIO = 30;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final ObtenerTipoDeCambioUseCase tipoDeCambio;
    private final SegmentosCambiariosPort segmentos;
    private final Clock reloj;

    public CotizarConversionService(ObtenerTipoDeCambioUseCase tipoDeCambio,
                                    SegmentosCambiariosPort segmentos, Clock reloj) {
        this.tipoDeCambio = tipoDeCambio;
        this.segmentos = segmentos;
        this.reloj = reloj;
    }

    @Override
    public Cotizacion cotizar(UUID usuarioId, Moneda origen) {
        LocalDate ayer = reloj.instant().atZone(LIMA).toLocalDate().minusDays(1);
        BigDecimal saldoPromedio = segmentos.saldoPromedioEnSoles(usuarioId,
                ayer.minusDays(DIAS_DE_PROMEDIO - 1L), ayer);
        SegmentoCambiario segmento = PoliticaDeSpread.segmentoPara(segmentos.segmentos(), saldoPromedio);
        return PoliticaDeSpread.cotizar(tipoDeCambio.vigente(), segmento, origen);
    }
}
