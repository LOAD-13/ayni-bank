package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.EnlaceDeRecuperacionInvalidoException;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;
import pe.ayni.bank.identity.domain.port.in.RecuperarContrasenaUseCase;

/**
 * El borde HTTP de HU-21. Lo que se afirma aqui es lo que ninguna otra capa puede
 * garantizar: que la respuesta de la solicitud es <strong>byte a byte</strong> la misma
 * exista o no la cuenta, que no sale antes de la duracion minima y como se traduce cada
 * error.
 */
class RecuperacionControllerTest {

    private static final String VALIDO = "token-valido";
    private static final String NUEVA = "Nueva!Clave2026#";

    private final CasoDeUsoFalso casoDeUso = new CasoDeUsoFalso();

    private MockMvc mvc(Duration duracionMinima) {
        return MockMvcBuilders.standaloneSetup(new RecuperacionController(casoDeUso, duracionMinima))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    private MvcResult solicitar(MockMvc mvc, String correo) throws Exception {
        return mvc.perform(post("/api/v1/recuperacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "190.12.4.7, 10.0.0.1")
                        .header("User-Agent", "Mozilla/5.0")
                        .content("{\"correo\":\"" + correo + "\"}"))
                .andReturn();
    }

    @Test
    @DisplayName("escenario 1: la misma respuesta, byte a byte, exista o no la cuenta")
    void respuestaIdentica() throws Exception {
        MockMvc mvc = mvc(Duration.ZERO);

        MvcResult existente = solicitar(mvc, "ana.quispe@example.pe");
        MvcResult inexistente = solicitar(mvc, "nadie@example.pe");

        assertThat(existente.getResponse().getStatus()).isEqualTo(202).isEqualTo(inexistente.getResponse().getStatus());
        assertThat(existente.getResponse().getContentAsString())
                .isEqualTo(inexistente.getResponse().getContentAsString())
                .contains(RecuperacionController.MENSAJE);
        assertThat(casoDeUso.correos).containsExactly("ana.quispe@example.pe", "nadie@example.pe");
        assertThat(casoDeUso.ultimoCliente.ip()).isEqualTo("190.12.4.7");
    }

    @Test
    @DisplayName("la respuesta no sale antes de la duracion minima")
    void duracionMinima() throws Exception {
        long inicio = System.nanoTime();
        solicitar(mvc(Duration.ofMillis(150)), "nadie@example.pe");

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isGreaterThanOrEqualTo(Duration.ofMillis(150));
    }

    @Test
    @DisplayName("un correo vacio es un 400 de validacion")
    void correoVacio() throws Exception {
        assertThat(solicitar(mvc(Duration.ZERO), "").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("validar un enlace vigente responde 204 y uno que no sirve 410")
    void validar() throws Exception {
        MockMvc mvc = mvc(Duration.ZERO);

        mvc.perform(post("/api/v1/recuperacion/validacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALIDO + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/recuperacion/validacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"caducado\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("https://ayni.pe/problemas/enlace-de-recuperacion-invalido"));
    }

    @Test
    @DisplayName("un token absurdamente largo se rechaza sin llegar al caso de uso")
    void tokenLargo() throws Exception {
        mvc(Duration.ZERO).perform(post("/api/v1/recuperacion/validacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "x".repeat(200) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(casoDeUso.validados).isEmpty();
    }

    @Test
    @DisplayName("confirmar responde 204, 400 si la contrasena incumple la politica y 410 si el enlace no sirve")
    void confirmar() throws Exception {
        MockMvc mvc = mvc(Duration.ZERO);

        mvc.perform(post("/api/v1/recuperacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALIDO + "\",\"contrasenaNueva\":\"" + NUEVA + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/recuperacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + VALIDO + "\",\"contrasenaNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/recuperacion/confirmacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"usado\",\"contrasenaNueva\":\"" + NUEVA + "\"}"))
                .andExpect(status().isGone());
        assertThat(casoDeUso.restablecidos).containsExactly(VALIDO);
    }

    @Test
    @DisplayName("los DTO no dejan el token ni la contrasena en una traza")
    void dtosOcultos() {
        assertThat(new RestablecimientoDeContrasenaDto(VALIDO, NUEVA).toString()).doesNotContain(VALIDO, NUEVA);
        assertThat(new EnlaceDeRecuperacionDto(VALIDO).toString()).doesNotContain(VALIDO);
        assertThat(new SolicitudDeRecuperacionDto("ana@example.pe").toString()).doesNotContain("ana");
    }

    private static final class CasoDeUsoFalso implements RecuperarContrasenaUseCase {
        private final List<String> correos = new ArrayList<>();
        private final List<String> validados = new ArrayList<>();
        private final List<String> restablecidos = new ArrayList<>();
        private HuellaDeCliente ultimoCliente;

        @Override
        public void solicitar(String correo, HuellaDeCliente cliente) {
            correos.add(correo);
            ultimoCliente = cliente;
        }

        @Override
        public void validar(String tokenEnClaro) {
            validados.add(tokenEnClaro);
            if (!VALIDO.equals(tokenEnClaro)) {
                throw new EnlaceDeRecuperacionInvalidoException();
            }
        }

        @Override
        public void restablecer(String tokenEnClaro, String contrasenaNueva, HuellaDeCliente cliente) {
            validar(tokenEnClaro);
            if (contrasenaNueva.length() < 12) {
                throw new ContrasenaInvalidaException(List.copyOf(Set.of(RequisitoDeContrasena.LONGITUD_MINIMA)));
            }
            restablecidos.add(tokenEnClaro);
        }
    }
}
