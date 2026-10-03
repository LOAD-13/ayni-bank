package pe.ayni.bank.core.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** El contrato HTTP de las operaciones: codigos, cabeceras y cuerpos · HU-07 y HU-08. */
class OperacionesControllerTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final String USUARIO = OperacionesController.CABECERA_USUARIO;
    private static final String CLAVE = OperacionesController.CABECERA_IDEMPOTENCIA;

    private final RepositorioDeCuentasPort cuentas = mock(RepositorioDeCuentasPort.class);
    private final LibroMayorPort libro = mock(LibroMayorPort.class);
    private final OperarCuentaUseCase operar = mock(OperarCuentaUseCase.class);
    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OperacionesController(cuentas, libro, operar,
                    new MetricasDeOperaciones(registro))).build();

    private final UUID titular = UUID.randomUUID();
    private final Cuenta cuenta = Cuenta.abrir(UUID.randomUUID(), titular, (short) 1,
            NumeroDeCuenta.de(Moneda.PEN, 1_000_000_030L), Moneda.PEN, AHORA);

    private Comprobante comprobante() {
        return new Comprobante(UUID.randomUUID(), TipoDeMovimiento.TRANSFERENCIA,
                Dinero.de("150.00", Moneda.PEN), "**********0030", "**********0031",
                "Cena · a **********0031", AHORA, Dinero.de("850.00", Moneda.PEN));
    }

    @Test
    @DisplayName("GET /cuentas/mia: la cuenta del titular del token, con saldo derivado")
    void miCuenta() throws Exception {
        when(cuentas.buscarActivaDe(titular, Moneda.PEN)).thenReturn(Optional.of(cuenta));
        when(libro.saldoDe(cuenta.id(), Moneda.PEN)).thenReturn(Dinero.de("1000", Moneda.PEN));
        when(cuentas.treaVigenteDe((short) 1)).thenReturn(Optional.of(new BigDecimal("0.045")));

        mvc.perform(get("/api/v1/cuentas/mia").header(USUARIO, titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value("1000.00"))
                .andExpect(jsonPath("$.trea").value("4.50"))
                .andExpect(jsonPath("$.numero").value(cuenta.numero().valor()));
    }

    @Test
    @DisplayName("sin la cabecera del gateway: 401, nunca datos")
    void sinIdentidad() throws Exception {
        mvc.perform(get("/api/v1/cuentas/mia"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sesion requerida"));
    }

    @Test
    @DisplayName("quien aun no tiene cuenta recibe 404 con explicacion")
    void sinCuenta() throws Exception {
        when(cuentas.buscarActivaDe(titular, Moneda.PEN)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/cuentas/mia").header(USUARIO, titular))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SIN_CUENTA"));
    }

    @Test
    @DisplayName("GET /movimientos: del mas reciente al mas antiguo, acotado a 50")
    void movimientos() throws Exception {
        when(cuentas.buscarActivaDe(titular, Moneda.PEN)).thenReturn(Optional.of(cuenta));
        when(libro.ultimosAsientosDe(eq(cuenta.id()), anyInt())).thenReturn(List.of(
                new Asiento(UUID.randomUUID(), cuenta.id(), UUID.randomUUID(), TipoDeAsiento.ABONO,
                        Dinero.de("300", Moneda.PEN), "Deposito simulado", AHORA)));

        mvc.perform(get("/api/v1/cuentas/mia/movimientos").param("limite", "500").header(USUARIO, titular))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ABONO"))
                .andExpect(jsonPath("$[0].importe").value("300.00"));

        verify(libro).ultimosAsientosDe(cuenta.id(), 50);
    }

    @Test
    @DisplayName("POST /transferencias: 201 con el comprobante y su ubicacion")
    void transferir() throws Exception {
        Comprobante c = comprobante();
        UUID clave = UUID.randomUUID();
        when(operar.transferir(eq(titular), eq("00111000000031"), any(), eq("Cena"), eq(clave)))
                .thenReturn(c);

        mvc.perform(post("/api/v1/transferencias").header(USUARIO, titular).header(CLAVE, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuentaDestino\":\"00111000000031\",\"importe\":\"150.00\",\"concepto\":\"Cena\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/movimientos/" + c.movimientoId()))
                .andExpect(jsonPath("$.importe").value("150.00"))
                .andExpect(jsonPath("$.saldoDisponible").value("850.00"))
                .andExpect(jsonPath("$.cuentaDestino").value("**********0031"));

        assertThat(registro.counter("ayni.operaciones", "tipo", "TRANSFERENCIA", "resultado", "ACEPTADA")
                .count()).isEqualTo(1.0);
        assertThat(registro.counter("ayni.operaciones.importe.soles", "tipo", "TRANSFERENCIA").count())
                .isEqualTo(c.importe().importe().doubleValue());
    }

    @Test
    @DisplayName("un importe que no es un decimal con dos cifras se rechaza con 400")
    void importeMalFormado() throws Exception {
        for (String importe : new String[] {"-5", "1e3", "10.555", "abc", ""}) {
            mvc.perform(post("/api/v1/transferencias").header(USUARIO, titular)
                            .header(CLAVE, UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"cuentaDestino\":\"00111000000031\",\"importe\":\"" + importe + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("sin Idempotency-Key no se mueve dinero: 400")
    void sinClave() throws Exception {
        mvc.perform(post("/api/v1/cuentas/mia/depositos-simulados").header(USUARIO, titular)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importe\":\"100\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Peticion incompleta"));
    }

    @Test
    @DisplayName("POST /depositos-simulados: 201 con el comprobante")
    void depositar() throws Exception {
        UUID clave = UUID.randomUUID();
        when(operar.depositarSimulado(eq(titular), any(), eq(clave))).thenReturn(comprobante());

        mvc.perform(post("/api/v1/cuentas/mia/depositos-simulados").header(USUARIO, titular)
                        .header(CLAVE, clave)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importe\":\"150\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("TRANSFERENCIA"));
    }

    @Test
    @DisplayName("una regla de negocio rota es 422 con el codigo del motivo")
    void rechazoDeNegocio() throws Exception {
        when(operar.transferir(any(), any(), any(), any(), any()))
                .thenThrow(new OperacionRechazadaException(MotivoDeRechazo.SALDO_INSUFICIENTE));

        mvc.perform(post("/api/v1/transferencias").header(USUARIO, titular).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuentaDestino\":\"00111000000031\",\"importe\":\"9999.00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"))
                .andExpect(jsonPath("$.title").value("Saldo insuficiente"));

        assertThat(registro.counter("ayni.operaciones.rechazos", "motivo", "SALDO_INSUFICIENTE").count())
                .isEqualTo(1.0);
        assertThat(registro.counter("ayni.operaciones", "tipo", "TRANSFERENCIA", "resultado", "RECHAZADA")
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("una clave reutilizada por otro titular es 409")
    void claveReutilizada() throws Exception {
        when(operar.depositarSimulado(any(), any(), any()))
                .thenThrow(new ClaveDeIdempotenciaReutilizadaException());

        mvc.perform(post("/api/v1/cuentas/mia/depositos-simulados").header(USUARIO, titular)
                        .header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importe\":\"10\"}"))
                .andExpect(status().isConflict());
    }
}
