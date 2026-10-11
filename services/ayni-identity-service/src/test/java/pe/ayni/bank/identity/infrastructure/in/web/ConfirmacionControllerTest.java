package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.identity.domain.model.CodigoDeConfirmacionIncorrectoException;
import pe.ayni.bank.identity.domain.model.ConfirmacionNoDisponibleException;
import pe.ayni.bank.identity.domain.model.OperacionAConfirmar;
import pe.ayni.bank.identity.domain.model.SinSegundoFactorException;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.ConfirmarOperacionUseCase;
import pe.ayni.bank.identity.domain.model.ConfirmacionEmitida;
import pe.ayni.bank.identity.domain.model.ConfirmacionIniciada;

/** El borde HTTP de la confirmacion de operaciones (HU-07, ADR-0031). */
class ConfirmacionControllerTest {

    private static final String CUERPO = """
            {"destino":"001-1100000-0-001","importe":"200.50","moneda":"PEN",\
            "claveIdempotencia":"7b3e1f2a-9c4d-4e5f-8a6b-1c2d3e4f5a6b"}""";

    private final ConfirmarOperacionUseCase casoDeUso = mock(ConfirmarOperacionUseCase.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConfirmacionController(casoDeUso))
            .setControllerAdvice(new ManejadorDeErrores()).build();
    private final UUID usuario = UUID.randomUUID();

    @Test
    @DisplayName("iniciar: 201 con el metodo; la operacion llega con la misma huella que calcula core")
    void iniciar() throws Exception {
        UUID id = UUID.randomUUID();
        var operacion = ArgumentCaptor.forClass(OperacionAConfirmar.class);
        when(casoDeUso.iniciar(eq(usuario), operacion.capture(), any())).thenReturn(new ConfirmacionIniciada(
                id, TipoDeSegundoFactor.APP_AUTENTICADORA, Instant.parse("2026-10-11T15:05:00Z")));

        mvc.perform(iniciar(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.confirmacionId").value(id.toString()))
                .andExpect(jsonPath("$.metodo").value("APP_AUTENTICADORA"));

        assertThat(operacion.getValue().huella()).isEqualTo("q1XjO0hmTgqtuZviV2Hpwi9zA8oSWwlVBYHZdNTmmSw=");
    }

    @Test
    @DisplayName("sin la cabecera de usuario: 401; con un importe mal escrito: 400")
    void validaciones() throws Exception {
        mvc.perform(post("/api/v1/confirmaciones").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        mvc.perform(iniciar(CUERPO.replace("200.50", "-3"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("verificar: 200 con el token; 422 con los intentos que quedan; 410 si ya no sirve; 409 sin 2FA")
    void verificar() throws Exception {
        UUID id = UUID.randomUUID();
        when(casoDeUso.verificar(eq(usuario), eq(id), eq("123456"), any()))
                .thenReturn(new ConfirmacionEmitida("jwt", Instant.parse("2026-10-11T15:05:00Z")));
        when(casoDeUso.verificar(eq(usuario), eq(id), eq("000000"), any()))
                .thenThrow(new CodigoDeConfirmacionIncorrectoException(2));
        when(casoDeUso.verificar(eq(usuario), eq(id), eq("111111"), any()))
                .thenThrow(new ConfirmacionNoDisponibleException());
        when(casoDeUso.iniciar(eq(usuario), any(), any())).thenThrow(new SinSegundoFactorException());

        mvc.perform(verificacion(id, "123456")).andExpect(status().isOk()).andExpect(jsonPath("$.token").value("jwt"));
        mvc.perform(verificacion(id, "000000")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.intentosRestantes").value(2));
        mvc.perform(verificacion(id, "111111")).andExpect(status().isGone());
        mvc.perform(iniciar(CUERPO)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("los DTO no dejan el codigo ni el token en una traza")
    void ocultos() {
        assertThat(new ConfirmacionController.CodigoDeConfirmacionDto("123456").toString()).doesNotContain("123456");
        assertThat(new ConfirmacionController.TokenDeConfirmacionDto("jwt-secreto", Instant.EPOCH).toString())
                .doesNotContain("jwt-secreto");
    }

    private RequestBuilder iniciar(String cuerpo) {
        return post("/api/v1/confirmaciones").header("X-Ayni-Usuario", usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private RequestBuilder verificacion(UUID id, String codigo) {
        return post("/api/v1/confirmaciones/" + id + "/verificacion").header("X-Ayni-Usuario", usuario)
                .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}");
    }
}
