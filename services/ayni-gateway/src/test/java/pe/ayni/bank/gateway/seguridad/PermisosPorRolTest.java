package pe.ayni.bank.gateway.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.yaml.snakeyaml.Yaml;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Los cinco roles contra las rutas reales del gateway (AYNI-281). Las reglas y las rutas
 * protegidas se leen de {@code application.yml}, no de una lista escrita aqui, y los permisos de
 * cada rol son los del catalogo de identity (V1): si alguien cambia una regla, esta matriz lo nota.
 */
class PermisosPorRolTest {

    private static final byte[] CLAVE = "ayni-firma-de-pruebas-de-32-bytes!!".getBytes();
    private static final Instant AHORA = Instant.parse("2026-11-24T15:00:00Z");
    private static final String USUARIO = "5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f";

    private static final List<String> CLIENTE = List.of(
            "cuenta:leer", "transferencia:iniciar", "tarjeta:administrar", "tarjeta:revelar_pan");
    private static final List<String> OPERADOR = List.of(
            "cuenta:leer_todas", "cliente:leer", "cliente:bloquear_iniciar");
    private static final List<String> SUPERVISOR = List.of(
            "cuenta:leer_todas", "cliente:leer", "transferencia:aprobar", "cliente:bloquear_aprobar");
    private static final List<String> AUDITOR = List.of(
            "cuenta:leer_todas", "cliente:leer", "auditoria:leer");
    private static final List<String> OFICIAL = List.of(
            "cliente:leer", "kyc:revisar", "kyc:marcar_sospechoso");

    private final FiltroDeAutenticacion filtro = new FiltroDeAutenticacion(
            new VerificadorDeTokens(Base64.getEncoder().encodeToString(CLAVE), Clock.fixed(AHORA, ZoneOffset.UTC)),
            configurada("rutas-protegidas"), new PermisosPorRuta(configurada("permisos-por-ruta")));

    private final AtomicReference<ServerWebExchange> reenviada = new AtomicReference<>();
    private final GatewayFilterChain cadena = intercambio -> {
        reenviada.set(intercambio);
        return Mono.empty();
    };

    @ParameterizedTest(name = "{0} {1} con {2}: {3}")
    @DisplayName("Matriz de roles por operacion")
    @CsvSource({
        // Lo que mueve dinero o muestra la cuenta propia es del cliente.
        "POST, /api/v1/transferencias,                      CLIENTE,    200",
        "POST, /api/v1/conversiones,                        CLIENTE,    200",
        "POST, /api/v1/cuentas/mia/depositos-simulados,     CLIENTE,    200",
        "POST, /api/v1/interbancarias,                      CLIENTE,    200",
        "GET,  /api/v1/cuentas/mia,                         CLIENTE,    200",
        "POST, /api/v1/cuentas/mia/dolares,                 CLIENTE,    200",
        "GET,  /api/v1/tipo-cambio/cotizacion,              CLIENTE,    200",
        "GET,  /api/v1/movimientos/abc,                     CLIENTE,    200",
        "GET,  /api/v1/tarjetas/mia,                        CLIENTE,    200",
        // El personal no tiene cuentas propias ni mueve dinero.
        "POST, /api/v1/transferencias,                      OPERADOR,   403",
        "POST, /api/v1/transferencias,                      SUPERVISOR, 403",
        "POST, /api/v1/transferencias,                      AUDITOR,    403",
        "POST, /api/v1/transferencias,                      OFICIAL,    403",
        "POST, /api/v1/conversiones,                        AUDITOR,    403",
        "POST, /api/v1/conversiones,                        OFICIAL,    403",
        "POST, /api/v1/cuentas/mia/depositos-simulados,     OPERADOR,   403",
        "POST, /api/v1/cuentas/mia/dolares,                 OPERADOR,   403",
        "GET,  /api/v1/cuentas/mia,                         AUDITOR,    403",
        "GET,  /api/v1/cuentas/mia,                         OFICIAL,    403",
        "GET,  /api/v1/movimientos/abc,                     SUPERVISOR, 403",
        "GET,  /api/v1/tarjetas/mia,                        OPERADOR,   403",
        "GET,  /api/v1/tarjetas/mia,                        AUDITOR,    403",
        "POST, /api/v1/interbancarias,                      OFICIAL,    403",
        // La pista de auditoria la lee el auditor y nadie mas.
        "GET,  /api/v1/auditoria/eventos,                   AUDITOR,    200",
        "GET,  /api/v1/auditoria/eventos,                   CLIENTE,    403",
        "GET,  /api/v1/auditoria/eventos,                   OPERADOR,   403",
        "GET,  /api/v1/auditoria/eventos,                   SUPERVISOR, 403",
        "GET,  /api/v1/auditoria/eventos,                   OFICIAL,    403",
        // Bloqueo de cuenta con cuatro ojos (AYNI-282): el operador inicia, el supervisor aprueba.
        "POST, /api/v1/backoffice/bloqueos,                 OPERADOR,   200",
        "POST, /api/v1/backoffice/bloqueos,                 SUPERVISOR, 403",
        "POST, /api/v1/backoffice/bloqueos,                 CLIENTE,    403",
        "POST, /api/v1/backoffice/bloqueos,                 AUDITOR,    403",
        "POST, /api/v1/backoffice/bloqueos/abc/aprobacion,  SUPERVISOR, 200",
        "POST, /api/v1/backoffice/bloqueos/abc/aprobacion,  OPERADOR,   403",
        "POST, /api/v1/backoffice/bloqueos/abc/aprobacion,  AUDITOR,    403",
        "GET,  /api/v1/backoffice/bloqueos,                 SUPERVISOR, 200",
        "GET,  /api/v1/backoffice/bloqueos,                 CLIENTE,    403",
    })
    void matriz(String metodo, String ruta, String rol, int esperado) throws Exception {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.valueOf(metodo), ruta)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(permisosDe(rol))));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        if (esperado == 200) {
            assertThat(reenviada.get()).as("la peticion debia llegar al servicio").isNotNull();
        } else {
            assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(reenviada.get()).as("no debia llegar al servicio").isNull();
        }
    }

    @Test
    @DisplayName("Un token de antes de los roles, sin el claim, sigue sirviendo como el de un cliente")
    void tokenAntiguoEsDeCliente() throws Exception {
        MockServerWebExchange transferir = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transferencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(null)));

        StepVerifier.create(filtro.filter(transferir, cadena)).verifyComplete();

        assertThat(reenviada.get()).isNotNull();
    }

    @Test
    @DisplayName("Un token nuevo sin ningun permiso no es un token antiguo: no puede nada")
    void tokenSinPermisosNoPuedeNada() throws Exception {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cuentas/mia")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(List.of())));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("El 403 es un problema RFC 7807 y es distinto del 401: hay sesion, falta permiso")
    void cuerpoDelProhibido() throws Exception {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transferencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(AUDITOR)));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getHeaders().getContentType().toString())
                .contains("application/problem+json");
        assertThat(intercambio.getResponse().getBodyAsString().block()).contains("\"status\":403");
    }

    @Test
    @DisplayName("Los permisos del token viajan a los servicios, y los que fabrica el cliente se descartan")
    void cabeceraDePermisos() throws Exception {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cuentas/mia")
                        .header(FiltroDeAutenticacion.CABECERA_PERMISOS, "auditoria:leer")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CLIENTE)));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(reenviada.get().getRequest().getHeaders().getFirst(FiltroDeAutenticacion.CABECERA_PERMISOS))
                .isEqualTo("cuenta:leer,tarjeta:administrar,tarjeta:revelar_pan,transferencia:iniciar");
    }

    @Test
    @DisplayName("En una ruta publica tampoco se acepta una cabecera de permisos fabricada")
    void cabeceraFabricadaEnRutaPublica() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/sesion")
                        .header(FiltroDeAutenticacion.CABECERA_PERMISOS, "auditoria:leer"));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(reenviada.get().getRequest().getHeaders()
                .containsHeader(FiltroDeAutenticacion.CABECERA_PERMISOS)).isFalse();
    }

    @Test
    @DisplayName("Una regla mal escrita impide arrancar en lugar de dejar una ruta sin proteger")
    void reglaMalEscrita() {
        List<String> sinPermiso = List.of("/api/v1/x");
        List<String> sinMetodoValido = List.of("POST/api/v1/x=a:b");
        assertThatThrownBy(() -> new PermisosPorRuta(sinPermiso))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PermisosPorRuta(sinMetodoValido))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Gana la primera regla que casa, y el metodo cuenta")
    void primeraReglaGana() {
        PermisosPorRuta reglas = new PermisosPorRuta(List.of(
                "POST:/api/v1/a=uno:escribir", "/api/v1/a=uno:leer", "/api/v1/b/**=dos:leer"));

        assertThat(reglas.exigidoPara(HttpMethod.POST, "/api/v1/a")).contains("uno:escribir");
        assertThat(reglas.exigidoPara(HttpMethod.GET, "/api/v1/a")).contains("uno:leer");
        assertThat(reglas.exigidoPara(HttpMethod.GET, "/api/v1/b/x/y")).contains("dos:leer");
        assertThat(reglas.exigidoPara(HttpMethod.GET, "/api/v1/c")).isEmpty();
    }

    private static List<String> permisosDe(String rol) {
        return switch (rol) {
            case "CLIENTE" -> CLIENTE;
            case "OPERADOR" -> OPERADOR;
            case "SUPERVISOR" -> SUPERVISOR;
            case "AUDITOR" -> AUDITOR;
            case "OFICIAL" -> OFICIAL;
            default -> throw new IllegalArgumentException(rol);
        };
    }

    /** Un token valido; {@code null} imita el de antes de los roles, sin el claim. */
    private static String token(List<String> permisos) throws Exception {
        JWTClaimsSet.Builder declaraciones = new JWTClaimsSet.Builder()
                .issuer("ayni-identity-service")
                .subject(USUARIO)
                .issueTime(Date.from(AHORA.minusSeconds(5)))
                .expirationTime(Date.from(AHORA.plusSeconds(600)));
        if (permisos != null) {
            declaraciones.claim("permisos", permisos);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), declaraciones.build());
        jwt.sign(new MACSigner(CLAVE));
        return jwt.serialize();
    }

    @SuppressWarnings("unchecked")
    private static List<String> configurada(String clave) {
        try (var entrada = new ClassPathResource("application.yml").getInputStream()) {
            for (Object documento : new Yaml().loadAll(entrada)) {
                if (documento instanceof Map<?, ?> raiz && raiz.get("ayni") instanceof Map<?, ?> ayni
                        && ayni.get("seguridad") instanceof Map<?, ?> seguridad
                        && seguridad.get(clave) instanceof String valor) {
                    return Arrays.stream(valor.split(",")).map(String::trim).toList();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("application.yml no define ayni.seguridad." + clave);
    }
}
