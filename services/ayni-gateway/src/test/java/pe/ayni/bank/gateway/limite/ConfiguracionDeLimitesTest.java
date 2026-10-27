package pe.ayni.bank.gateway.limite;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import pe.ayni.bank.gateway.seguridad.FiltroDeAutenticacion;
import reactor.test.StepVerifier;

/** Con que clave se cuenta cada peticion: de ella depende quien comparte cupo con quien. */
class ConfiguracionDeLimitesTest {

    private final ConfiguracionDeLimites configuracion = new ConfiguracionDeLimites();
    private final KeyResolver porIp = configuracion.porIp();
    private final KeyResolver porUsuario = configuracion.porUsuario();

    private static MockServerHttpRequest.BaseBuilder<?> peticion() {
        return MockServerHttpRequest.post("/api/v1/sesion").remoteAddress(new InetSocketAddress("192.0.2.4", 50000));
    }

    @Test
    @DisplayName("Detras de Caddy cuenta el ultimo X-Forwarded-For: los anteriores los puede inventar el cliente")
    void ultimoXForwardedFor() {
        var intercambio = MockServerWebExchange.from(peticion().header("X-Forwarded-For", "198.51.100.7, 203.0.113.9"));

        StepVerifier.create(porIp.resolve(intercambio)).expectNext("ip:203.0.113.9").verifyComplete();
    }

    @Test
    @DisplayName("Sin X-Forwarded-For cuenta la direccion de la conexion")
    void direccionDeLaConexion() {
        StepVerifier.create(porIp.resolve(MockServerWebExchange.from(peticion()))).expectNext("ip:192.0.2.4").verifyComplete();
    }

    @Test
    @DisplayName("Con sesion, el dinero se cuenta por el titular que fijo el filtro de autenticacion")
    void porTitular() {
        var intercambio = MockServerWebExchange.from(peticion().header(FiltroDeAutenticacion.CABECERA_USUARIO, "u-1"));

        StepVerifier.create(porUsuario.resolve(intercambio)).expectNext("usuario:u-1").verifyComplete();
    }

    @Test
    @DisplayName("Sin titular, la cuenta por usuario cae a la IP en lugar de dejar la peticion sin clave")
    void sinTitularCaeALaIp() {
        StepVerifier.create(porUsuario.resolve(MockServerWebExchange.from(peticion()))).expectNext("ip:192.0.2.4").verifyComplete();
    }
}
