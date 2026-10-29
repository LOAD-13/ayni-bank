package pe.ayni.bank.gateway.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.yaml.snakeyaml.Yaml;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * T-37 (ADR-0038): toda consulta de cuentas exige sesión, también la que usaba la pantalla
 * final del registro con el titular en la URL. Se prueba contra las rutas protegidas que de
 * verdad configura el gateway, no contra una lista escrita en la prueba.
 */
class RutasProtegidasDeLaCuentaTest {

    private final AtomicReference<ServerWebExchange> reenviada = new AtomicReference<>();
    private final GatewayFilterChain cadena = intercambio -> {
        reenviada.set(intercambio);
        return Mono.empty();
    };

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/cuentas/titular/5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f",
        "/api/v1/cuentas/mia",
        "/api/v1/cuentas/mia/movimientos",
        "/api/v1/cuentas"
    })
    void sinSesionNingunaConsultaDeCuentasLlegaAlServicio(String ruta) throws IOException {
        FiltroDeAutenticacion filtro = new FiltroDeAutenticacion(
                new VerificadorDeTokens(Base64.getEncoder().encodeToString(new byte[32]), Clock.systemUTC()),
                rutasProtegidasConfiguradas());
        MockServerWebExchange intercambio = MockServerWebExchange.from(MockServerHttpRequest.get(ruta));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reenviada.get()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static List<String> rutasProtegidasConfiguradas() throws IOException {
        try (var entrada = new ClassPathResource("application.yml").getInputStream()) {
            for (Object documento : new Yaml().loadAll(entrada)) {
                if (documento instanceof Map<?, ?> raiz && raiz.get("ayni") instanceof Map<?, ?> ayni
                        && ayni.get("seguridad") instanceof Map<?, ?> seguridad
                        && seguridad.get("rutas-protegidas") instanceof String rutas) {
                    return Arrays.stream(rutas.split(",")).map(String::trim).toList();
                }
            }
        }
        throw new IllegalStateException("application.yml no define ayni.seguridad.rutas-protegidas");
    }
}
