package pe.ayni.bank.core.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.domain.model.TipoDeCambioCambioException;
import pe.ayni.bank.core.domain.port.in.ConfirmarConversionUseCase;
import pe.ayni.bank.core.domain.port.in.CotizarConversionUseCase;

/** El contrato HTTP de la cotizacion y la conversion · HU-12. */
class ConversionesControllerTest {

    private static final Instant AHORA = Instant.parse("2026-11-23T19:00:00Z");
    private static final String USUARIO = OperacionesController.CABECERA_USUARIO;
    private static final String CLAVE = OperacionesController.CABECERA_IDEMPOTENCIA;
    private static final SegmentoCambiario ESTANDAR =
            new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005000"), BigDecimal.ZERO);

    private final CotizarConversionUseCase cotizar = mock(CotizarConversionUseCase.class);
    private final ConfirmarConversionUseCase confirmar = mock(ConfirmarConversionUseCase.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ConversionesController(cotizar, confirmar)).build();
    private final UUID titular = UUID.randomUUID();

    private static Cotizacion cotizacion(String tipo, boolean enCache) {
        return new Cotizacion(new TipoDeCambioAplicado(new BigDecimal(tipo), AHORA, enCache),
                new BigDecimal("3.752000"), ESTANDAR, "BCRP");
    }

    private Conversion conversion() {
        Cuenta soles = Cuenta.abrir(UUID.randomUUID(), titular, (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 1_000_000_101L), Moneda.PEN, AHORA);
        Cuenta dolares = Cuenta.abrir(UUID.randomUUID(), titular, (short) 2,
                NumeroDeCuenta.de(Moneda.USD, 1_000_000_102L), Moneda.USD, AHORA);
        Cuenta posicionPen = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 4L), Moneda.PEN, AHORA);
        Cuenta posicionUsd = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 2,
                NumeroDeCuenta.de(Moneda.USD, 4L), Moneda.USD, AHORA);
        return Conversion.de(UUID.randomUUID(), soles, Dinero.de("500.00", Moneda.PEN), dolares, posicionPen,
                posicionUsd, Dinero.de("376.14", Moneda.PEN), cotizacion("3.761380", false), AHORA);
    }

    @Test
    @DisplayName("GET /tipo-cambio/cotizacion con importe: tipo, referencia, margen, segmento y lo que se recibiria")
    void cotizacionConImporte() throws Exception {
        when(cotizar.cotizar(titular, Moneda.PEN)).thenReturn(cotizacion("3.761380", false));

        mvc.perform(get("/api/v1/tipo-cambio/cotizacion").param("origen", "PEN").param("importe", "376.14")
                        .header(USUARIO, titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("PEN"))
                .andExpect(jsonPath("$.destino").value("USD"))
                .andExpect(jsonPath("$.tipoAplicado").value("3.761380"))
                .andExpect(jsonPath("$.tipoReferencia").value("3.752000"))
                .andExpect(jsonPath("$.margen").value("0.50"))
                .andExpect(jsonPath("$.segmento").value("ESTANDAR"))
                .andExpect(jsonPath("$.enCache").value(false))
                .andExpect(jsonPath("$.importeOrigen").value("376.14"))
                .andExpect(jsonPath("$.importeDestino").value("100.00"));
    }

    @Test
    @DisplayName("GET /tipo-cambio/cotizacion sin importe: solo el tipo, sin importes")
    void cotizacionSinImporte() throws Exception {
        when(cotizar.cotizar(titular, Moneda.USD)).thenReturn(cotizacion("3.735638", true));

        mvc.perform(get("/api/v1/tipo-cambio/cotizacion").param("origen", "USD").header(USUARIO, titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destino").value("PEN"))
                .andExpect(jsonPath("$.enCache").value(true))
                .andExpect(jsonPath("$.importeDestino").doesNotExist());
    }

    @Test
    @DisplayName("Sin tipo oficial ni ultimo conocido: 503, nunca un tipo inventado")
    void sinTipo() throws Exception {
        when(cotizar.cotizar(any(), any())).thenThrow(new FuenteDeTipoDeCambioNoDisponibleException("sin datos"));

        mvc.perform(get("/api/v1/tipo-cambio/cotizacion").param("origen", "PEN").header(USUARIO, titular))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("TIPO_DE_CAMBIO_NO_DISPONIBLE"));
    }

    @Test
    @DisplayName("Sin la cabecera del gateway: 401")
    void sinIdentidad() throws Exception {
        mvc.perform(get("/api/v1/tipo-cambio/cotizacion").param("origen", "PEN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /conversiones: 201 con lo recibido, el tipo y su referencia, margen y segmento")
    void convertir() throws Exception {
        Conversion hecha = conversion();
        when(confirmar.confirmar(eq(titular), eq(Dinero.de("376.14", Moneda.PEN)), eq(new BigDecimal("3.761380")),
                eq(false), any())).thenReturn(hecha);

        mvc.perform(post("/api/v1/conversiones").header(USUARIO, titular).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"PEN\",\"importe\":\"376.14\",\"tipoMostrado\":\"3.761380\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/movimientos/" + hecha.salida().id()))
                .andExpect(jsonPath("$.importeOrigen").value("376.14"))
                .andExpect(jsonPath("$.monedaDestino").value("USD"))
                .andExpect(jsonPath("$.importeDestino").value("100.00"))
                .andExpect(jsonPath("$.tipoAplicado").value("3.761380"))
                .andExpect(jsonPath("$.tipoReferencia").value("3.752000"))
                .andExpect(jsonPath("$.margen").value("0.50"))
                .andExpect(jsonPath("$.segmento").value("ESTANDAR"));
    }

    @Test
    @DisplayName("Si el tipo cambio: 409 con la cotizacion nueva dentro del problema")
    void tipoCambio() throws Exception {
        when(confirmar.confirmar(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new TipoDeCambioCambioException(cotizacion("3.770000", true), Moneda.PEN));

        mvc.perform(post("/api/v1/conversiones").header(USUARIO, titular).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"PEN\",\"importe\":\"100.00\",\"tipoMostrado\":\"3.761380\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIPO_DE_CAMBIO_CAMBIO"))
                .andExpect(jsonPath("$.cotizacion.tipoAplicado").value("3.770000"))
                .andExpect(jsonPath("$.cotizacion.enCache").value(true));
    }

    @Test
    @DisplayName("Saldo insuficiente o sin cuenta en dolares: el motivo de la regla, no un 500")
    void rechazada() throws Exception {
        when(confirmar.confirmar(any(), any(), any(), anyBoolean(), any()))
                .thenThrow(new OperacionRechazadaException(MotivoDeRechazo.SIN_CUENTA));

        mvc.perform(post("/api/v1/conversiones").header(USUARIO, titular).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"PEN\",\"importe\":\"100.00\",\"tipoMostrado\":\"3.761380\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_CUENTA"));
    }

    @Test
    @DisplayName("Un tipo o un importe mal formado: 400 y no se intenta convertir")
    void malFormado() throws Exception {
        mvc.perform(post("/api/v1/conversiones").header(USUARIO, titular).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"PEN\",\"importe\":\"1e3\",\"tipoMostrado\":\"abc\"}"))
                .andExpect(status().isBadRequest());
        verify(confirmar, never()).confirmar(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Sin Idempotency-Key: 400")
    void sinClave() throws Exception {
        mvc.perform(post("/api/v1/conversiones").header(USUARIO, titular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"PEN\",\"importe\":\"100.00\",\"tipoMostrado\":\"3.761380\"}"))
                .andExpect(status().isBadRequest());
    }
}
