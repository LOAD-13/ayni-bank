package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.Cookie;
import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.SesionExpiradaException;
import pe.ayni.bank.identity.domain.model.SesionIniciada;
import pe.ayni.bank.identity.domain.port.in.IniciarSesionUseCase;

/**
 * El borde HTTP del ingreso · HU-04.
 *
 * <p>Lo que importa comprobar aquí no es que Spring sepa enrutar, sino las decisiones que
 * este controlador toma y que ninguna otra capa puede corregir: **dónde acaba cada token**,
 * **qué atributos lleva la cookie** y **de dónde sale la IP** que va a la pista de
 * auditoría. Todo eso se puede afirmar sin levantar el contexto.
 */
class SesionControllerTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final String TOKEN_DE_RENOVACION = "token-de-renovacion-en-claro";

    private final CasoDeUsoFalso casoDeUso = new CasoDeUsoFalso();

    /** Como se despliega en local: sobre HTTP, con la cookie sin `Secure`. */
    private final SesionController enLocal = new SesionController(casoDeUso, false);

    /** Como se despliega en staging y producción: HTTPS y cookie `Secure`. */
    private final SesionController enProduccion = new SesionController(casoDeUso, true);

    private static MockHttpServletRequest peticionDesde(String ipReenviada) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/sesion");
        peticion.setRemoteAddr("172.18.0.7");
        if (ipReenviada != null) {
            peticion.addHeader("X-Forwarded-For", ipReenviada);
        }
        return peticion;
    }

    private static String cookieDe(ResponseEntity<?> respuesta) {
        return respuesta.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    @Nested
    @DisplayName("Paso 1 · credenciales")
    class Credenciales {

        @Test
        @DisplayName("no entrega ningún token: el ingreso no termina aquí")
        void devuelveSoloElDesafio() {
            var respuesta = enLocal.presentarCredenciales(
                    new CredencialesDto("ana@example.pe", "Cont!rasena2026#"),
                    peticionDesde(null), "Mozilla/5.0");

            assertThat(respuesta.getBody().desafioId()).isNotBlank();
            assertThat(cookieDe(respuesta)).isNull();
        }

        @Test
        @DisplayName("la primera vez entrega el URI para dar de alta el segundo factor")
        void entregaElUriDeInscripcion() {
            casoDeUso.requiereInscripcion = true;

            var respuesta = enLocal.presentarCredenciales(
                    new CredencialesDto("ana@example.pe", "Cont!rasena2026#"),
                    peticionDesde(null), "Mozilla/5.0");

            assertThat(respuesta.getBody().requiereInscripcion()).isTrue();
            assertThat(respuesta.getBody().uriDeAprovisionamiento()).startsWith("otpauth://");
        }
    }

    @Nested
    @DisplayName("La IP que llega a la pista de auditoría")
    class IpDelCliente {

        @Test
        @DisplayName("se toma de X-Forwarded-For, no la del gateway")
        void tomaLaDelCliente() {
            // Todo pasa por el gateway, así que `getRemoteAddr()` devolvería siempre la
            // misma dirección interna y la auditoría no serviría para nada.
            enLocal.presentarCredenciales(
                    new CredencialesDto("ana@example.pe", "clave"),
                    peticionDesde("190.12.4.7, 10.0.0.1"), "Mozilla/5.0");

            assertThat(casoDeUso.ultimoCliente.ip()).isEqualTo("190.12.4.7");
        }

        @Test
        @DisplayName("sin esa cabecera cae en la dirección directa")
        void caeEnLaDirecta() {
            enLocal.presentarCredenciales(
                    new CredencialesDto("ana@example.pe", "clave"),
                    peticionDesde(null), "Mozilla/5.0");

            assertThat(casoDeUso.ultimoCliente.ip()).isEqualTo("172.18.0.7");
        }

        @Test
        void unAgenteAusenteNoRompeNada() {
            enLocal.presentarCredenciales(
                    new CredencialesDto("ana@example.pe", "clave"),
                    peticionDesde(null), null);

            assertThat(casoDeUso.ultimoCliente.agenteDeUsuario()).isEqualTo("desconocido");
        }
    }

    @Nested
    @DisplayName("Paso 2 · segundo factor")
    class SegundoFactor {

        private ResponseEntity<SesionDto> verificar(SesionController controlador) {
            return controlador.verificarSegundoFactor(
                    new SegundoFactorDto(UUID.randomUUID().toString(), "049713"),
                    peticionDesde("190.12.4.7"), "Mozilla/5.0");
        }

        @Test
        @DisplayName("el token de acceso va en el cuerpo y el de renovación NO")
        void elRepartoDeLosTokens() {
            // Es la decisión de seguridad principal de HU-04: si el de renovación viajara
            // en el cuerpo, cualquier XSS se llevaría siete días de sesión.
            var respuesta = verificar(enLocal);

            assertThat(respuesta.getBody().tokenDeAcceso()).isNotBlank();
            assertThat(respuesta.getBody().toString()).doesNotContain(TOKEN_DE_RENOVACION);
            assertThat(cookieDe(respuesta)).contains(TOKEN_DE_RENOVACION);
        }

        @Test
        @DisplayName("la cookie es HttpOnly y SameSite=Strict")
        void atributosDeLaCookie() {
            String cookie = cookieDe(verificar(enLocal));

            // HttpOnly: JavaScript no la lee, así que un XSS no se la lleva.
            // SameSite=Strict: no viaja en navegaciones desde otros sitios, que es la
            // puerta de un CSRF.
            assertThat(cookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/");
        }

        @Test
        @DisplayName("el nombre y el atributo Secure dependen del entorno")
        void laCookieCambiaConElEntorno() {
            // El prefijo `__Host-` EXIGE `Secure`. Sobre HTTP el navegador descartaría la
            // cookie sin decir nada: la respuesta sería 200 y la renovación fallaría
            // después sin motivo aparente.
            assertThat(cookieDe(verificar(enLocal)))
                    .startsWith("ayni-renovacion=")
                    .doesNotContain("Secure");

            assertThat(cookieDe(verificar(enProduccion)))
                    .startsWith("__Host-ayni-renovacion=")
                    .contains("Secure");
        }
    }

    @Nested
    @DisplayName("Renovación")
    class Renovacion {

        private MockHttpServletRequest conCookie(String nombre) {
            MockHttpServletRequest peticion = peticionDesde("190.12.4.7");
            peticion.setCookies(new Cookie(nombre, TOKEN_DE_RENOVACION));
            return peticion;
        }

        @Test
        void renuevaConLaCookieDelEntorno() {
            var respuesta = enLocal.renovar(conCookie("ayni-renovacion"), "Mozilla/5.0");

            assertThat(respuesta.getBody().tokenDeAcceso()).isNotBlank();
            assertThat(casoDeUso.ultimoTokenRenovado).isEqualTo(TOKEN_DE_RENOVACION);
        }

        @Test
        @DisplayName("sin cookie la sesión se da por caducada, sin llamar al caso de uso")
        void sinCookieNoHaySesion() {
            assertThatThrownBy(() -> enLocal.renovar(peticionDesde(null), "Mozilla/5.0"))
                    .isInstanceOf(SesionExpiradaException.class);

            assertThat(casoDeUso.ultimoTokenRenovado).isNull();
        }

        @Test
        @DisplayName("una cookie con otro nombre se ignora")
        void ignoraLasCookiesAjenas() {
            assertThatThrownBy(() ->
                    enLocal.renovar(conCookie("__Host-ayni-renovacion"), "Mozilla/5.0"))
                    .isInstanceOf(SesionExpiradaException.class);
        }
    }

    // ─── Doble ─────────────────────────────────────────────────────────────

    private static final class CasoDeUsoFalso implements IniciarSesionUseCase {
        private final List<ComandoDeIngreso> ingresos = new ArrayList<>();
        private HuellaDeCliente ultimoCliente;
        private String ultimoTokenRenovado;
        private boolean requiereInscripcion;

        @Override
        public DesafioAbierto presentarCredenciales(ComandoDeIngreso comando) {
            ingresos.add(comando);
            ultimoCliente = comando.cliente();

            return requiereInscripcion
                    ? DesafioAbierto.conInscripcion(UUID.randomUUID(),
                            "otpauth://totp/Ayni?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
                    : DesafioAbierto.paraQuienYaTieneSegundoFactor(UUID.randomUUID());
        }

        @Override
        public SesionIniciada verificarSegundoFactor(ComandoDeSegundoFactor comando) {
            ultimoCliente = comando.cliente();
            return sesion();
        }

        @Override
        public SesionIniciada renovar(String tokenDeRenovacion, HuellaDeCliente cliente) {
            ultimoTokenRenovado = tokenDeRenovacion;
            ultimoCliente = cliente;
            return sesion();
        }

        private static SesionIniciada sesion() {
            return new SesionIniciada("jwt-de-prueba", AHORA.plusSeconds(900),
                    TOKEN_DE_RENOVACION, AHORA.plusSeconds(604800));
        }
    }
}
