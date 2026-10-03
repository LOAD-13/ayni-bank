package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** La aprobacion por operador solo responde con la clave configurada. */
class OperadorControllerTest {

    private final List<UUID> aprobadas = new ArrayList<>();

    private OperadorController controlador(String claveConfigurada) {
        return new OperadorController(aprobadas::add, claveConfigurada);
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
}
