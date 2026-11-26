package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.domain.model.TipoDeCambioCambioException;
import pe.ayni.bank.core.domain.port.in.ConvertirMonedaUseCase;
import pe.ayni.bank.core.domain.port.in.CotizarConversionUseCase;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;

/** Confirmar vuelve a cotizar y solo convierte si el cliente vio el tipo que se aplicaria (HU-12). */
class ConfirmarConversionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-23T19:00:00Z");
    private static final UUID TITULAR = UUID.randomUUID();
    private static final Dinero IMPORTE = Dinero.de("370.00", Moneda.PEN);
    private static final SegmentoCambiario ESTANDAR =
            new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005000"), BigDecimal.ZERO);

    private final CotizarConversionUseCase cotizar = mock(CotizarConversionUseCase.class);
    private final ConvertirMonedaUseCase convertir = mock(ConvertirMonedaUseCase.class);
    private final RegistroDeIdempotenciaPort idempotencia = mock(RegistroDeIdempotenciaPort.class);
    private final Conversion hecha = mock(Conversion.class);
    private ConfirmarConversionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ConfirmarConversionService(cotizar, convertir, idempotencia);
        when(idempotencia.resultadoDe(any())).thenReturn(Optional.empty());
        when(convertir.convertir(any(), any(), any(), any())).thenReturn(hecha);
    }

    private static Cotizacion cotizacion(String tipo, boolean enCache) {
        return new Cotizacion(new TipoDeCambioAplicado(new BigDecimal(tipo), AHORA, enCache),
                new BigDecimal("3.752000"), ESTANDAR, "BCRP");
    }

    @Test
    @DisplayName("Con el tipo que el cliente vio, convierte con la cotizacion vigente")
    void convierteSiCoincide() {
        Cotizacion vigente = cotizacion("3.761380", false);
        when(cotizar.cotizar(TITULAR, Moneda.PEN)).thenReturn(vigente);
        UUID clave = UUID.randomUUID();

        // El cliente lo manda con menos ceros: 3.76138 es el mismo numero que 3.761380.
        Conversion c = servicio.confirmar(TITULAR, IMPORTE, new BigDecimal("3.76138"), false, clave);

        assertThat(c).isSameAs(hecha);
        verify(convertir).convertir(TITULAR, IMPORTE, vigente, clave);
    }

    @Test
    @DisplayName("Si el tipo cambio, no convierte y devuelve la cotizacion nueva")
    void rechazaSiCambio() {
        Cotizacion nueva = cotizacion("3.770000", false);
        when(cotizar.cotizar(TITULAR, Moneda.PEN)).thenReturn(nueva);

        assertThatThrownBy(() -> servicio.confirmar(TITULAR, IMPORTE, new BigDecimal("3.761380"), false,
                UUID.randomUUID()))
                .isInstanceOfSatisfying(TipoDeCambioCambioException.class, e -> {
                    assertThat(e.nueva()).isSameAs(nueva);
                    assertThat(e.origen()).isEqualTo(Moneda.PEN);
                });
        verify(convertir, never()).convertir(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Escenario 2: si ahora el tipo viene del ultimo conocido y el cliente no lo sabia, no convierte")
    void rechazaCacheNoAvisada() {
        when(cotizar.cotizar(TITULAR, Moneda.PEN)).thenReturn(cotizacion("3.761380", true));

        BigDecimal visto = new BigDecimal("3.761380");
        UUID clave = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.confirmar(TITULAR, IMPORTE, visto, false, clave))
                .isInstanceOf(TipoDeCambioCambioException.class);
        verify(convertir, never()).convertir(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Si el cliente vio el aviso de ultimo conocido y acepto, convierte")
    void convierteCacheAvisada() {
        when(cotizar.cotizar(TITULAR, Moneda.PEN)).thenReturn(cotizacion("3.761380", true));

        assertThat(servicio.confirmar(TITULAR, IMPORTE, new BigDecimal("3.761380"), true, UUID.randomUUID()))
                .isSameAs(hecha);
    }

    @Test
    @DisplayName("Un reintento de una conversion ya hecha la devuelve aunque el tipo se haya movido")
    void reintentoNoSeRechaza() {
        UUID clave = UUID.randomUUID();
        when(idempotencia.resultadoDe(clave)).thenReturn(Optional.of(UUID.randomUUID()));
        when(cotizar.cotizar(TITULAR, Moneda.PEN)).thenReturn(cotizacion("3.790000", false));

        assertThat(servicio.confirmar(TITULAR, IMPORTE, new BigDecimal("3.761380"), false, clave))
                .isSameAs(hecha);
    }
}
