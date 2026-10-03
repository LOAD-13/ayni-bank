package pe.ayni.bank.gateway.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.test.StepVerifier;

import pe.ayni.bank.gateway.seguridad.FiltroDeAutenticacion;

/** A quien cuenta el limite de tasa y que responde al superarlo · AYNI-160. */
class LimiteDeTasaTest {

    private final ConfiguracionDeLimiteDeTasa configuracion = new ConfiguracionDeLimiteDeTasa();

    @Test
    @DisplayName("detras de Caddy cuenta la IP real del cliente, la primera de X-Forwarded-For")
    void ipDetrasDelProxy() {
        var intercambio = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/sesion")
                .header("X-Forwarded-For", "190.12.4.7, 172.18.0.2")
                .remoteAddress(new InetSocketAddress("172.18.0.2", 41000)));

        StepVerifier.create(configuracion.porIp().resolve(intercambio))
                .expectNext("ip:190.12.4.7").verifyComplete();
    }

    @Test
    @DisplayName("sin proxy delante cuenta la direccion de la conexion")
    void ipSinProxy() {
        var intercambio = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/registro")
                .remoteAddress(new InetSocketAddress("10.0.0.9", 50000)));

        StepVerifier.create(configuracion.porIp().resolve(intercambio))
                .expectNext("ip:10.0.0.9").verifyComplete();
    }

    @Test
    @DisplayName("sin direccion conocida no falla: cuenta en un grupo comun")
    void ipDesconocida() {
        var intercambio = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/sesion"));

        StepVerifier.create(configuracion.porIp().resolve(intercambio))
                .expectNext("ip:desconocida").verifyComplete();
    }

    @Test
    @DisplayName("las operaciones con dinero se cuentan por titular; sin titular, por IP")
    void porUsuario() {
        var conSesion = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/transferencias")
                .header(FiltroDeAutenticacion.CABECERA_USUARIO, "6f1c0d2e-0000-4000-8000-000000000001"));
        var sinSesion = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/transferencias")
                .header("X-Forwarded-For", "190.12.4.7"));

        StepVerifier.create(configuracion.porUsuario().resolve(conSesion))
                .expectNext("usuario:6f1c0d2e-0000-4000-8000-000000000001").verifyComplete();
        StepVerifier.create(configuracion.porUsuario().resolve(sinSesion))
                .expectNext("ip:190.12.4.7").verifyComplete();
    }

    @Test
    @DisplayName("una respuesta 429 lleva Retry-After; las demas no")
    void retryAfter() {
        CabeceraRetryAfter filtro = configuracion.cabeceraRetryAfter();
        var limitada = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/sesion"));
        var normal = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/sesion"));

        filtro.filter(limitada, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();
        }).block();
        filtro.filter(normal, ex -> ex.getResponse().setComplete()).block();

        assertThat(limitada.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(normal.getResponse().getHeaders().getFirst("Retry-After")).isNull();
    }
}
