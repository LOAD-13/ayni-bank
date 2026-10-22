package pe.ayni.bank.core.infrastructure.in.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** Toda peticion mal formada recibe el mismo 400, sin repetir lo recibido · ISO/IEC 25010, interoperabilidad. */
class ManejadorDeErroresTest {

    private static final String USUARIO = OperacionesController.CABECERA_USUARIO;
    private static final String CLAVE = OperacionesController.CABECERA_IDEMPOTENCIA;

    private final OperarCuentaUseCase operar = mock(OperarCuentaUseCase.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OperacionesController(mock(RepositorioDeCuentasPort.class), mock(LibroMayorPort.class),
                    operar, new MetricasDeOperaciones(new SimpleMeterRegistry())))
            .setControllerAdvice(new ManejadorDeErrores())
            .build();

    @Test
    @DisplayName("Un importe con formato no valido: 400 que nombra el campo, y la operacion no se intenta")
    void importeNoValido() throws Exception {
        mvc.perform(post("/api/v1/cuentas/mia/depositos-simulados")
                        .header(USUARIO, UUID.randomUUID()).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importe\":\"diez soles\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.errores[0].campo").value("importe"))
                .andExpect(content().string(not(containsString("diez soles"))));
        verifyNoInteractions(operar);
    }

    @Test
    @DisplayName("Una transferencia sin cuenta de destino: 400 con cada campo que falla, en orden estable")
    void camposObligatorios() throws Exception {
        mvc.perform(post("/api/v1/transferencias")
                        .header(USUARIO, UUID.randomUUID()).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cuentaDestino\":\"\",\"importe\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaDestino"))
                .andExpect(jsonPath("$.errores[1].campo").value("importe"));
        verifyNoInteractions(operar);
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON: 400 sin el mensaje interno del parser")
    void cuerpoQueNoEsJson() throws Exception {
        mvc.perform(post("/api/v1/cuentas/mia/depositos-simulados")
                        .header(USUARIO, UUID.randomUUID()).header(CLAVE, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{importe: <script>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString("Jackson"))));
    }

    @Test
    @DisplayName("Un identificador de movimiento que no es UUID: 400 que nombra el parametro, no un 500")
    void identificadorQueNoEsUuid() throws Exception {
        mvc.perform(get("/api/v1/movimientos/no-es-un-uuid").header(USUARIO, UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("movimientoId")))
                // El valor solo aparece en `instance`, la ruta que el propio cliente pidio (RFC 7807).
                .andExpect(jsonPath("$.detail").value(not(containsString("no-es-un-uuid"))));
    }
}
