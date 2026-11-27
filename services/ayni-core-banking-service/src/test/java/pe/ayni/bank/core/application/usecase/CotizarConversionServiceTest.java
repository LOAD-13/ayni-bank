package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.model.TipoDeCambioVigente;
import pe.ayni.bank.core.domain.port.in.ObtenerTipoDeCambioUseCase;
import pe.ayni.bank.core.domain.port.out.SegmentosCambiariosPort;

class CotizarConversionServiceTest {

    /** 14:00 en Lima, lunes 23 de noviembre de 2026. */
    private static final Instant AHORA = Instant.parse("2026-11-23T19:00:00Z");
    private static final UUID TITULAR = UUID.randomUUID();
    private static final SegmentoCambiario ESTANDAR =
            new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005000"), new BigDecimal("0.0000"));
    private static final SegmentoCambiario PREFERENCIAL =
            new SegmentoCambiario("PREFERENCIAL", new BigDecimal("0.002500"), new BigDecimal("5000.0000"));

    private final ObtenerTipoDeCambioUseCase tipoDeCambio = mock(ObtenerTipoDeCambioUseCase.class);
    private final SegmentosCambiariosPort segmentos = mock(SegmentosCambiariosPort.class);
    private CotizarConversionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CotizarConversionService(tipoDeCambio, segmentos, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(tipoDeCambio.vigente()).thenReturn(new TipoDeCambioVigente(new TipoDeCambioOficial(
                LocalDate.parse("2026-11-20"), new BigDecimal("3.745000"), new BigDecimal("3.752000"), "BCRP"),
                AHORA, false));
        when(segmentos.segmentos()).thenReturn(List.of(ESTANDAR, PREFERENCIAL));
    }

    @Test
    @DisplayName("El saldo promedio se mide en los 30 dias cerrados que terminan ayer, hora de Lima")
    void ventanaDeTreintaDias() {
        when(segmentos.saldoPromedioEnSoles(any(), any(), any())).thenReturn(BigDecimal.ZERO);

        servicio.cotizar(TITULAR, Moneda.PEN);

        verify(segmentos).saldoPromedioEnSoles(TITULAR, LocalDate.parse("2026-10-24"), LocalDate.parse("2026-11-22"));
    }

    @Test
    @DisplayName("Con saldo promedio de S/ 5 000 o mas se aplica la tarifa preferencial")
    void clientePreferencial() {
        when(segmentos.saldoPromedioEnSoles(any(), any(), any())).thenReturn(new BigDecimal("5000.00"));

        Cotizacion c = servicio.cotizar(TITULAR, Moneda.PEN);

        assertThat(c.segmento()).isEqualTo(PREFERENCIAL);
        assertThat(c.tipo().solesPorDolar()).isEqualTo(new BigDecimal("3.756690"));
    }

    @Test
    @DisplayName("Por debajo de ese saldo, o sin historial, se aplica la tarifa estandar")
    void clienteEstandar() {
        when(segmentos.saldoPromedioEnSoles(any(), any(), any())).thenReturn(new BigDecimal("4999.99"));

        Cotizacion c = servicio.cotizar(TITULAR, Moneda.PEN);

        assertThat(c.segmento()).isEqualTo(ESTANDAR);
        assertThat(c.tipo().solesPorDolar()).isEqualTo(new BigDecimal("3.761380"));
    }
}
