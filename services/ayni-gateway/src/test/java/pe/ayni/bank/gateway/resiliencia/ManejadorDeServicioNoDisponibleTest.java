package pe.ayni.bank.gateway.resiliencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.test.StepVerifier;

/** Un servicio que rechaza la conexion se traduce en 503 con Retry-After, no en 500 (ADR-0034). */
class ManejadorDeServicioNoDisponibleTest {

    private final ManejadorDeServicioNoDisponible manejador = new ManejadorDeServicioNoDisponible();

    private static MockServerWebExchange intercambio() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/cuentas/mia"));
    }

    @Test
    @DisplayName("Conexion rechazada por el servicio: 503, Retry-After y problem+json")
    void conexionRechazada() {
        MockServerWebExchange intercambio = intercambio();
        // Netty la envuelve: el rechazo puede venir como causa y no como el error de arriba.
        Throwable error = new IllegalStateException("fallo al enrutar", new ConnectException("Connection refused"));

        StepVerifier.create(manejador.handle(intercambio, error)).verifyComplete();

        var respuesta = intercambio.getResponse();
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(respuesta.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        StepVerifier.create(respuesta.getBodyAsString())
                .assertNext(cuerpo -> assertThat(cuerpo).contains("SERVICIO_NO_DISPONIBLE").contains("\"status\":503"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Cualquier otro error sigue su camino: el manejador no lo convierte en 503")
    void otrosErroresNoSeTocan() {
        IllegalArgumentException otro = new IllegalArgumentException("otra cosa");

        StepVerifier.create(manejador.handle(intercambio(), otro)).expectErrorMatches(e -> e == otro).verify();
    }
}
