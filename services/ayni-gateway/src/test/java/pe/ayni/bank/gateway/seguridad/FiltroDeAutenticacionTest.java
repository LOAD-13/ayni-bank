package pe.ayni.bank.gateway.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class FiltroDeAutenticacionTest {

    private static final byte[] CLAVE = "ayni-firma-de-pruebas-de-32-bytes!!".getBytes();
    private static final String CLAVE_B64 = Base64.getEncoder().encodeToString(CLAVE);
    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final String USUARIO = "5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f";

    private final VerificadorDeTokens verificador =
            new VerificadorDeTokens(CLAVE_B64, Clock.fixed(AHORA, ZoneOffset.UTC));
    private final FiltroDeAutenticacion filtro = new FiltroDeAutenticacion(
            verificador, List.of("/api/v1/cuentas/mia", "/api/v1/transferencias/**"));

    /** Cadena falsa que recuerda la peticion que le llego, si le llego alguna. */
    private final AtomicReference<ServerWebExchange> reenviada = new AtomicReference<>();
    private final GatewayFilterChain cadena = intercambio -> {
        reenviada.set(intercambio);
        return Mono.empty();
    };

    @Test
    void sinTokenEnRutaProtegidaResponde401YNoLlegaAlServicio() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cuentas/mia"));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(intercambio.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer");
        assertThat(reenviada.get()).isNull();
    }

    @Test
    void conTokenValidoReenviaConElUsuarioDelToken() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transferencias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(CLAVE, "ayni-identity-service",
                                AHORA.plusSeconds(600), USUARIO)));

        // `/api/v1/transferencias/**` casa tambien con la ruta desnuda en AntPathMatcher.
        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(reenviada.get()).isNotNull();
        assertThat(reenviada.get().getRequest().getHeaders()
                .getFirst(FiltroDeAutenticacion.CABECERA_USUARIO)).isEqualTo(USUARIO);
    }

    @Test
    void laCabeceraDeUsuarioQueMandaElClienteSeDescartaSiempre() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/registro")
                        .header(FiltroDeAutenticacion.CABECERA_USUARIO, "otro-usuario"));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(reenviada.get().getRequest().getHeaders()
                .containsKey(FiltroDeAutenticacion.CABECERA_USUARIO)).isFalse();
    }

    @Test
    void laCabeceraFabricadaNoSustituyeAlTokenEnRutaProtegida() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cuentas/mia")
                        .header(FiltroDeAutenticacion.CABECERA_USUARIO, USUARIO));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reenviada.get()).isNull();
    }

    @Test
    void lasRutasPublicasPasanSinToken() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/sesion"));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(reenviada.get()).isNotNull();
        assertThat(intercambio.getResponse().getStatusCode()).isNull();
    }

    @Test
    void elPreflightCorsPasaSinToken() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/cuentas/mia"));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(reenviada.get()).isNotNull();
    }

    @Test
    void unaCabeceraQueNoEsBearerSeRechaza() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cuentas/mia")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXN1YXJpbzpjbGF2ZQ=="));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unBearerVacioSeRechaza() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/cuentas/mia")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer   "));

        StepVerifier.create(filtro.filter(intercambio, cadena)).verifyComplete();

        assertThat(intercambio.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void seEjecutaAntesQueElEnrutado() {
        assertThat(filtro.getOrder()).isLessThan(Ordered.LOWEST_PRECEDENCE);
        assertThat(filtro.getOrder()).isLessThan(0);
    }

    // ── VerificadorDeTokens ────────────────────────────────────────────────

    @Test
    void unTokenCaducadoNoIdentificaANadie() throws Exception {
        String caducado = token(CLAVE, "ayni-identity-service", AHORA.minusSeconds(1), USUARIO);
        assertThat(verificador.usuarioDe(caducado)).isEmpty();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoIdentificaANadie() throws Exception {
        byte[] otra = "otra-clave-cualquiera-de-32-bytes!!!".getBytes();
        assertThat(verificador.usuarioDe(token(otra, "ayni-identity-service", AHORA.plusSeconds(60), USUARIO)))
                .isEmpty();
    }

    @Test
    void unTokenDeOtroEmisorNoIdentificaANadie() throws Exception {
        assertThat(verificador.usuarioDe(token(CLAVE, "otro-emisor", AHORA.plusSeconds(60), USUARIO)))
                .isEmpty();
    }

    @Test
    void unTokenSinSujetoNoIdentificaANadie() throws Exception {
        assertThat(verificador.usuarioDe(token(CLAVE, "ayni-identity-service", AHORA.plusSeconds(60), null)))
                .isEmpty();
    }

    @Test
    void unTokenSinCaducidadNoIdentificaANadie() throws Exception {
        assertThat(verificador.usuarioDe(token(CLAVE, "ayni-identity-service", null, USUARIO))).isEmpty();
    }

    @Test
    void elAlgoritmoNoneSeRechaza() {
        String sinFirma = new PlainJWT(new JWTClaimsSet.Builder()
                .issuer("ayni-identity-service").subject(USUARIO)
                .expirationTime(Date.from(AHORA.plusSeconds(60))).build()).serialize();
        assertThat(verificador.usuarioDe(sinFirma)).isEmpty();
    }

    @Test
    void basuraNoIdentificaANadie() {
        assertThat(verificador.usuarioDe("esto.no.es-un-jwt")).isEmpty();
    }

    @Test
    void unaClaveCortaImpideArrancar() {
        String corta = Base64.getEncoder().encodeToString("corta".getBytes());
        Clock reloj = Clock.systemUTC();
        assertThatThrownBy(() -> new VerificadorDeTokens(corta, reloj))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void laConfiguracionCreaElFiltroConLasRutasIndicadas() {
        ConfiguracionDeSeguridad configuracion = new ConfiguracionDeSeguridad();
        VerificadorDeTokens creado = configuracion.verificadorDeTokens(CLAVE_B64);
        FiltroDeAutenticacion conRutas = configuracion.filtroDeAutenticacion(creado, List.of("/x"));
        assertThat(conRutas).isNotNull();
    }

    private static String token(byte[] clave, String emisor, Instant expira, String sujeto) {
        try {
            JWTClaimsSet.Builder declaraciones = new JWTClaimsSet.Builder()
                    .issuer(emisor)
                    .issueTime(Date.from(AHORA.minusSeconds(5)));
            if (expira != null) {
                declaraciones.expirationTime(Date.from(expira));
            }
            if (sujeto != null) {
                declaraciones.subject(sujeto);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), declaraciones.build());
            jwt.sign(new MACSigner(clave));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
