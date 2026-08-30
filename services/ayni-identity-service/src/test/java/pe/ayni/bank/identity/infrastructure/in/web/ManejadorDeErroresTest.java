package pe.ayni.bank.identity.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import pe.ayni.bank.identity.domain.model.ConsentimientoNoOtorgadoException;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.CredencialesInvalidasException;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.CuentaInhabilitadaException;
import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;
import pe.ayni.bank.identity.domain.model.ReutilizacionDeRefreshTokenException;
import pe.ayni.bank.identity.domain.model.SegundoFactorInvalidoException;
import pe.ayni.bank.identity.domain.model.SesionExpiradaException;

/**
 * Traducción de las excepciones a RFC 7807.
 *
 * <p>Lo que se comprueba aquí no es que Spring sepa serializar un `ProblemDetail`, sino las
 * dos cosas que sí pueden salir mal y que importan: que cada fallo lleve el código de estado
 * correcto —un 401 y un 423 se tratan de forma distinta en el cliente— y que **ningún
 * mensaje revele si un correo pertenece a un cliente de Ayni**.
 */
class ManejadorDeErroresTest {

    private final ManejadorDeErrores manejador = new ManejadorDeErrores();
    private final WebRequest peticion =
            new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/sesion"));

    @SuppressWarnings("unchecked")
    private static List<ErrorDeCampoDto> erroresDe(ProblemDetail problema) {
        Object errores = problema.getProperties() == null
                ? null
                : problema.getProperties().get("errores");
        return errores == null ? List.of() : (List<ErrorDeCampoDto>) errores;
    }

    /** Vista mínima del registro interno del manejador, para poder afirmar sobre él. */
    private interface ErrorDeCampoDto {
    }

    @Test
    void laValidacionDevuelveLosErroresOrdenadosYPorCampo() {
        BindingResult enlace = new BeanPropertyBindingResult(new Object(), "solicitud");
        enlace.rejectValue(null, "x", "no importa");
        enlace.addError(new org.springframework.validation.FieldError(
                "solicitud", "celular", "El celular debe tener nueve digitos."));
        enlace.addError(new org.springframework.validation.FieldError(
                "solicitud", "apellidos", "Escribe tus apellidos."));

        ProblemDetail problema = manejador.alFallarLaValidacion(
                new MethodArgumentNotValidException(null, enlace), peticion);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        // Orden estable: sin él, dos peticiones idénticas devuelven los mismos errores en
        // distinto orden y cualquier prueba que compare la respuesta entera falla a ratos.
        assertThat(problema.getProperties()).containsKey("errores");
        assertThat(erroresDe(problema)).hasSize(2);
        assertThat(String.valueOf(problema.getProperties().get("errores")))
                .startsWith("[ErrorDeCampo[campo=apellidos");
    }

    @Test
    void laPoliticaDeContrasenaDetallaCadaRequisitoIncumplido() {
        ProblemDetail problema = manejador.alIncumplirLaPolitica(
                new ContrasenaInvalidaException(List.of(
                        RequisitoDeContrasena.MAYUSCULA, RequisitoDeContrasena.SIMBOLO)),
                peticion);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(erroresDe(problema)).hasSize(2);
        assertThat(String.valueOf(problema.getType())).contains("politica-de-contrasena");
    }

    @Test
    void elConsentimientoQueFaltaSeMarcaSobreSuCasilla() {
        ProblemDetail problema = manejador.alFaltarElConsentimiento(
                new ConsentimientoNoOtorgadoException(), peticion);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(String.valueOf(problema.getProperties().get("errores")))
                .contains("aceptaTerminos");
    }

    @Test
    @DisplayName("las credenciales invalidas son 401 con el mensaje neutro")
    void credencialesInvalidas() {
        ProblemDetail problema = manejador.alFallarLasCredenciales(
                new CredencialesInvalidasException(), peticion);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problema.getDetail())
                .isEqualTo(CredencialesInvalidasException.MENSAJE_NEUTRO);
    }

    @Test
    @DisplayName("nada de lo que se responde dice si el correo existe")
    void ningunMensajeDelataLaCuenta() {
        // Es el criterio que recorre HU-01 y HU-04. Si algun dia alguien anade aqui un
        // «ese correo no esta registrado» por amabilidad, esta prueba lo para.
        for (ProblemDetail problema : List.of(
                manejador.alFallarLasCredenciales(new CredencialesInvalidasException(), peticion),
                manejador.alFallarElSegundoFactor(new SegundoFactorInvalidoException(), peticion),
                manejador.alEstarBloqueado(
                        new CuentaBloqueadaException(Duration.ofMinutes(5)), peticion),
                manejador.alCaducarLaSesion(new SesionExpiradaException(), peticion))) {

            assertThat(problema.getTitle() + " " + problema.getDetail())
                    .doesNotContainIgnoringCase("no existe")
                    .doesNotContainIgnoringCase("no esta registrad")
                    .doesNotContainIgnoringCase("no encontrad")
                    .doesNotContainIgnoringCase("usuario desconocido");
        }
    }

    @Test
    @DisplayName("el bloqueo es 423 y lleva la espera restante para la cuenta atras")
    void ingresoPausado() {
        ProblemDetail problema = manejador.alEstarBloqueado(
                new CuentaBloqueadaException(Duration.ofSeconds(277)), peticion);

        // 423 y no 429: no es que se hayan hecho demasiadas peticiones, es que este recurso
        // concreto esta bloqueado.
        assertThat(problema.getStatus()).isEqualTo(HttpStatus.LOCKED.value());
        assertThat(problema.getProperties().get("esperaSegundos")).isEqualTo(277L);
    }

    @Test
    @DisplayName("la cuenta inhabilitada es 403: no se levanta sola")
    void cuentaInhabilitada() {
        ProblemDetail problema = manejador.alEstarInhabilitada(
                new CuentaInhabilitadaException(), peticion);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("la reutilizacion de un token responde igual que una sesion caducada")
    void reutilizacionYCaducidadSeVenIgual() {
        // Decirle a quien reutilizo un token que se le detecto solo le informa de que no
        // vale la pena insistir con ese. Al titular si se le avisa, por correo.
        ProblemDetail porReutilizacion = manejador.alCaducarLaSesion(
                new ReutilizacionDeRefreshTokenException(UUID.randomUUID()), peticion);
        ProblemDetail porCaducidad = manejador.alCaducarLaSesion(
                new SesionExpiradaException(), peticion);

        assertThat(porReutilizacion.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(porReutilizacion.getTitle()).isEqualTo(porCaducidad.getTitle());
        assertThat(porReutilizacion.getDetail()).isEqualTo(porCaducidad.getDetail());
    }

    @Test
    void losObjetosDeValorQueRechazanSuEntradaSon400() {
        ProblemDetail problema = manejador.alRecibirUnValorInvalido(
                new IllegalArgumentException("El DNI debe tener ocho digitos."), peticion);

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getDetail()).isEqualTo("El DNI debe tener ocho digitos.");
    }

    @Test
    @DisplayName("un fallo no previsto no filtra el mensaje de la excepcion")
    void elErrorInternoEsDeliberadamenteVago() {
        // Un mensaje real filtra nombres de tablas, versiones de bibliotecas y rutas del
        // servidor, que es material de reconocimiento gratuito.
        ProblemDetail problema = manejador.alFallarInesperadamente(
                new IllegalStateException("relation \"identity.usuario\" does not exist"),
                peticion);

        assertThat(problema.getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problema.getDetail())
                .doesNotContain("identity.usuario")
                .doesNotContain("relation");
    }

    @Test
    void todaRespuestaLlevaSuTipoYLaRutaQueLaProvoco() {
        ProblemDetail problema = manejador.alFallarLasCredenciales(
                new CredencialesInvalidasException(), peticion);

        assertThat(String.valueOf(problema.getType())).startsWith("https://ayni.pe/problemas/");
        assertThat(String.valueOf(problema.getInstance())).isEqualTo("/api/v1/sesion");
    }
}
