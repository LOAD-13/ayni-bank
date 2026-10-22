package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeSolicitud;
import pe.ayni.bank.identity.domain.model.SolicitudNoRechazableException;
import pe.ayni.bank.identity.infrastructure.in.web.OperadorController.SolicitudDeRechazo;

/** La aprobacion y el rechazo por operador solo responden con la clave configurada. */
class OperadorControllerTest {

    private final List<UUID> aprobadas = new ArrayList<>();
    private final List<Map.Entry<UUID, MotivoDeRechazoDeSolicitud>> rechazadas = new ArrayList<>();

    private OperadorController controlador(String claveConfigurada) {
        return new OperadorController(aprobadas::add,
                (solicitudId, motivo) -> rechazadas.add(Map.entry(solicitudId, motivo)), claveConfigurada);
    }

    @Test
    @DisplayName("con la clave correcta aprueba la solicitud")
    void conLaClaveCorrectaAprueba() {
        UUID solicitudId = UUID.randomUUID();

        controlador("s3creta").aprobar(solicitudId, "s3creta");

        assertThat(aprobadas).containsExactly(solicitudId);
    }

    @Test
    @DisplayName("con otra clave, sin clave o sin clave configurada responde 404 y no aprueba")
    void sinLaClaveCorrectaNoAprueba() {
        UUID solicitudId = UUID.randomUUID();

        for (var intento : List.of(
                (Runnable) () -> controlador("s3creta").aprobar(solicitudId, "otra"),
                () -> controlador("s3creta").aprobar(solicitudId, null),
                () -> controlador("").aprobar(solicitudId, ""))) {
            assertThatThrownBy(intento::run)
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        assertThat(aprobadas).isEmpty();
    }

    @Test
    @DisplayName("AYNI-327 · con la clave correcta rechaza la solicitud con su motivo")
    void conLaClaveCorrectaRechaza() {
        UUID solicitudId = UUID.randomUUID();

        controlador("s3creta").rechazar(solicitudId, "s3creta",
                new SolicitudDeRechazo(MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE));

        assertThat(rechazadas).containsExactly(Map.entry(solicitudId, MotivoDeRechazoDeSolicitud.SELFIE_NO_COINCIDE));
    }

    @Test
    @DisplayName("AYNI-327 · sin la clave correcta responde 404 y no rechaza")
    void sinLaClaveCorrectaNoRechaza() {
        UUID solicitudId = UUID.randomUUID();
        SolicitudDeRechazo cuerpo = new SolicitudDeRechazo(MotivoDeRechazoDeSolicitud.DATOS_NO_COINCIDEN);

        for (var intento : List.of(
                (Runnable) () -> controlador("s3creta").rechazar(solicitudId, "otra", cuerpo),
                () -> controlador("s3creta").rechazar(solicitudId, null, cuerpo),
                () -> controlador("").rechazar(solicitudId, "", cuerpo))) {
            assertThatThrownBy(intento::run)
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        assertThat(rechazadas).isEmpty();
    }

    @Test
    @DisplayName("AYNI-327 · una solicitud ya cerrada responde 409; una inexistente o señuelo, 404")
    void erroresDelRechazo() {
        OperadorController controlador = controlador("s3creta");

        assertThat(controlador.alNoPoderRechazar(SolicitudNoRechazableException.cerrada("APROBADA")).getStatus())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(controlador.alNoPoderRechazar(SolicitudNoRechazableException.inexistente()).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("AYNI-327 · por HTTP: 202 con un motivo de la lista; 400 sin motivo o con uno inventado; 409 si ya se cerro")
    void rechazoPorHttp() throws Exception {
        UUID abierta = UUID.randomUUID();
        UUID cerrada = UUID.randomUUID();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OperadorController(aprobadas::add,
                        (solicitudId, motivo) -> {
                            if (solicitudId.equals(cerrada)) {
                                throw SolicitudNoRechazableException.cerrada("APROBADA");
                            }
                            rechazadas.add(Map.entry(solicitudId, motivo));
                        }, "s3creta"))
                .setControllerAdvice(new ManejadorDeErrores()).build();

        mvc.perform(rechazo(abierta, "{\"motivo\":\"DOCUMENTO_NO_VALIDO\"}")).andExpect(status().isAccepted());
        mvc.perform(rechazo(abierta, "{}")).andExpect(status().isBadRequest());
        mvc.perform(rechazo(abierta, "{\"motivo\":\"PORQUE_SI\"}")).andExpect(status().isBadRequest());
        mvc.perform(rechazo(cerrada, "{\"motivo\":\"DOCUMENTO_NO_VALIDO\"}")).andExpect(status().isConflict());

        assertThat(rechazadas).containsExactly(Map.entry(abierta, MotivoDeRechazoDeSolicitud.DOCUMENTO_NO_VALIDO));
    }

    private static MockHttpServletRequestBuilder rechazo(UUID solicitudId, String cuerpo) {
        return post("/api/v1/operador/solicitudes/{id}/rechazar", solicitudId)
                .header("X-Operador-Clave", "s3creta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }
}
