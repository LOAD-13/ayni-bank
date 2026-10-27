package pe.ayni.bank.gateway.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Limite de tasa de extremo a extremo (AYNI-160, ADR-0035): el gateway real, un Redis real y
 * un servicio de detras falso que siempre responde 200. Lo que no es 200 lo ha decidido el
 * gateway.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class LimiteDeTasaTest {

    private static final String CLAVE = "YXluaS1maXJtYS1kZS1kZXNhcnJvbGxvLTMyYnl0ZXM=";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final DisposableServer SERVICIO = HttpServer.create()
            .port(0)
            .route(rutas -> rutas.route(peticion -> true,
                    (peticion, respuesta) -> respuesta.status(200).sendString(Mono.just("ok"))))
            .bindNow();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.data.redis.host", REDIS::getHost);
        registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registro.add("AYNI_IDENTITY_URI", () -> "http://localhost:" + SERVICIO.port());
        registro.add("AYNI_CORE_URI", () -> "http://localhost:" + SERVICIO.port());
    }

    @AfterAll
    static void apagarServicio() {
        SERVICIO.disposeNow();
    }

    @Autowired
    private WebTestClient cliente;

    private WebTestClient.ResponseSpec post(String ruta, String ip) {
        return cliente.post().uri(ruta).header("X-Forwarded-For", ip).exchange();
    }

    @Test
    @DisplayName("Inicio de sesion: 5 por minuto por IP; la sexta recibe 429 con Retry-After")
    void sesionCincoPorMinuto() {
        for (int i = 0; i < 5; i++) {
            post("/api/v1/sesion", "203.0.113.10").expectStatus().isOk();
        }

        HttpHeaders cabeceras = post("/api/v1/sesion", "203.0.113.10")
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .returnResult(Void.class).getResponseHeaders();

        // 12 fichas por intento a una por segundo: como mucho 12 s hasta el siguiente.
        assertThat(Long.parseLong(cabeceras.getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1L, 12L);
    }

    @Test
    @DisplayName("El cupo es por IP: otra direccion no hereda el bloqueo")
    void cupoPorIp() {
        for (int i = 0; i < 5; i++) {
            post("/api/v1/sesion", "203.0.113.20").expectStatus().isOk();
        }
        post("/api/v1/sesion", "203.0.113.20").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        post("/api/v1/sesion", "203.0.113.21").expectStatus().isOk();
    }

    @Test
    @DisplayName("Registro: 3 por minuto por IP")
    void registroTresPorMinuto() {
        for (int i = 0; i < 3; i++) {
            post("/api/v1/registro", "203.0.113.30").expectStatus().isOk();
        }
        post("/api/v1/registro", "203.0.113.30").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("El desafio por correo tiene ruta: /api/v1/segundo-factor/** llega a identity y no da 404")
    void segundoFactorTieneRuta() {
        post("/api/v1/segundo-factor/desafio/usuario/u-1/generar", "203.0.113.40").expectStatus().isOk();
    }

    @Test
    @DisplayName("Segundo factor: 10 por minuto, contando juntas sus dos rutas")
    void segundoFactorDiezPorMinuto() {
        for (int i = 0; i < 5; i++) {
            post("/api/v1/sesion/segundo-factor", "203.0.113.50").expectStatus().isOk();
            post("/api/v1/segundo-factor/desafio/d-1/verificar", "203.0.113.50").expectStatus().isOk();
        }
        post("/api/v1/sesion/segundo-factor", "203.0.113.50").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Renovacion del token: 20 por minuto")
    void renovacionVeintePorMinuto() {
        for (int i = 0; i < 20; i++) {
            post("/api/v1/sesion/renovacion", "203.0.113.60").expectStatus().isOk();
        }
        post("/api/v1/sesion/renovacion", "203.0.113.60").expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("URL de subida de documentos: 10 por hora, con Retry-After de minutos")
    void urlDeSubidaDiezPorHora() {
        for (int i = 0; i < 10; i++) {
            post("/api/v1/solicitudes/s-1/documentos/url-de-subida", "203.0.113.70").expectStatus().isOk();
        }

        HttpHeaders cabeceras = post("/api/v1/solicitudes/s-1/documentos/url-de-subida", "203.0.113.70")
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .returnResult(Void.class).getResponseHeaders();

        assertThat(Long.parseLong(cabeceras.getFirst(HttpHeaders.RETRY_AFTER))).isBetween(300L, 360L);
    }

    @Test
    @DisplayName("Transferencias y depositos: 10 por minuto por usuario, no por IP")
    void dineroPorUsuario() throws Exception {
        String ana = "Bearer " + token("usuario-ana");
        String beto = "Bearer " + token("usuario-beto");

        for (int i = 0; i < 5; i++) {
            transferencia("/api/v1/transferencias", ana).expectStatus().isOk();
            transferencia("/api/v1/cuentas/mia/depositos-simulados", ana).expectStatus().isOk();
        }
        transferencia("/api/v1/transferencias", ana).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Misma IP, otro titular: su cupo esta intacto.
        transferencia("/api/v1/transferencias", beto).expectStatus().isOk();
    }

    @Test
    @DisplayName("Las rutas que no se limitan siguen igual")
    void rutasSinLimite() {
        for (int i = 0; i < 8; i++) {
            cliente.get().uri("/api/v1/cuentas/titular/u-1").header("X-Forwarded-For", "203.0.113.90")
                    .exchange().expectStatus().isOk();
        }
    }

    private WebTestClient.ResponseSpec transferencia(String ruta, String autorizacion) {
        return cliente.post().uri(ruta)
                .header("X-Forwarded-For", "203.0.113.80")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .exchange();
    }

    private static String token(String usuario) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer("ayni-identity-service")
                .subject(usuario)
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build());
        jwt.sign(new MACSigner(Base64.getDecoder().decode(CLAVE)));
        return jwt.serialize();
    }
}
