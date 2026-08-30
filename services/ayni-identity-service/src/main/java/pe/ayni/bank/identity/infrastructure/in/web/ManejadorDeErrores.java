package pe.ayni.bank.identity.infrastructure.in.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import pe.ayni.bank.identity.domain.model.ConsentimientoNoOtorgadoException;
import pe.ayni.bank.identity.domain.model.ContrasenaInvalidaException;
import pe.ayni.bank.identity.domain.model.CredencialesInvalidasException;
import pe.ayni.bank.identity.domain.model.CuentaBloqueadaException;
import pe.ayni.bank.identity.domain.model.CuentaInhabilitadaException;
import pe.ayni.bank.identity.domain.model.ReutilizacionDeRefreshTokenException;
import pe.ayni.bank.identity.domain.model.SegundoFactorInvalidoException;
import pe.ayni.bank.identity.domain.model.SesionExpiradaException;

/**
 * Traduce las excepciones a {@code application/problem+json}, segun RFC 7807.
 *
 * <p>Un formato de error propio por servicio obliga a cada cliente a aprender uno
 * distinto. RFC 7807 es el que Spring ya construye con {@link ProblemDetail}, y el que el
 * contrato OpenAPI declara.
 *
 * <p><strong>Ningun mensaje de error de este fichero revela si un correo esta
 * registrado.</strong> Ese caso no llega hasta aqui: el servicio responde 202 tambien
 * cuando la cuenta existe. Lo que se detalla son incumplimientos de formato y de politica,
 * que la persona necesita saber para corregir y que no informan de nada ajeno.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    private static final String BASE_DE_TIPOS = "https://ayni.pe/problemas/";

    /** Errores de las anotaciones de validacion sobre el DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail alFallarLaValidacion(MethodArgumentNotValidException excepcion,
                                              WebRequest peticion) {
        List<ErrorDeCampo> errores = new ArrayList<>();
        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.add(new ErrorDeCampo(error.getField(), error.getDefaultMessage())));
        // Orden estable: sin el, dos peticiones identicas devuelven los mismos errores en
        // distinto orden y cualquier prueba que compare la respuesta entera falla a ratos.
        errores.sort(Comparator.comparing(ErrorDeCampo::campo));

        return problema(HttpStatus.BAD_REQUEST, "validacion",
                "Los datos enviados no son validos", null, errores, peticion);
    }

    /** Escenario 3: la contrasena no cumple la politica. */
    @ExceptionHandler(ContrasenaInvalidaException.class)
    public ProblemDetail alIncumplirLaPolitica(ContrasenaInvalidaException excepcion,
                                               WebRequest peticion) {
        List<ErrorDeCampo> errores = excepcion.incumplidos().stream()
                .map(requisito -> new ErrorDeCampo("contrasena", requisito.mensaje()))
                .toList();

        return problema(HttpStatus.BAD_REQUEST, "politica-de-contrasena",
                "La contrasena no cumple la politica", null, errores, peticion);
    }

    /** Escenario 4: no se aceptaron los terminos. */
    @ExceptionHandler(ConsentimientoNoOtorgadoException.class)
    public ProblemDetail alFaltarElConsentimiento(ConsentimientoNoOtorgadoException excepcion,
                                                  WebRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "consentimiento-obligatorio",
                "Falta el consentimiento", excepcion.getMessage(),
                List.of(new ErrorDeCampo("aceptaTerminos", excepcion.getMessage())), peticion);
    }

    /**
     * HU-04, escenarios 2 y 3: correo desconocido, contrasena incorrecta o codigo invalido.
     *
     * <p>Los tres comparten manejador <strong>a proposito</strong>. Si el correo inexistente
     * devolviera un tipo de problema distinto del de la contrasena incorrecta, el formulario
     * de ingreso se convertiria en un comprobador de cuentas: bastaria comparar respuestas
     * para saber quien es cliente de Ayni. El unico que se distingue es el segundo factor,
     * y solo porque para llegar ahi ya hubo que acertar la contrasena.
     */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ProblemDetail alFallarLasCredenciales(CredencialesInvalidasException excepcion,
                                                 WebRequest peticion) {
        return problema(HttpStatus.UNAUTHORIZED, "credenciales-invalidas",
                "No pudimos verificar tus datos", excepcion.getMessage(),
                List.of(), peticion);
    }

    @ExceptionHandler(SegundoFactorInvalidoException.class)
    public ProblemDetail alFallarElSegundoFactor(SegundoFactorInvalidoException excepcion,
                                                 WebRequest peticion) {
        return problema(HttpStatus.UNAUTHORIZED, "segundo-factor-invalido",
                "El codigo no es valido", excepcion.getMessage(),
                List.of(new ErrorDeCampo("codigo", excepcion.getMessage())), peticion);
    }

    /**
     * Escenario 3: el ingreso esta pausado.
     *
     * <p>423 y no 429: no es que se hayan hecho demasiadas peticiones, es que este recurso
     * concreto esta bloqueado. La espera restante va como extension del problema porque la
     * pantalla aprobada muestra una cuenta atras.
     */
    @ExceptionHandler(CuentaBloqueadaException.class)
    public ProblemDetail alEstarBloqueado(CuentaBloqueadaException excepcion,
                                          WebRequest peticion) {
        ProblemDetail detalle = problema(HttpStatus.LOCKED, "ingreso-pausado",
                "Ingreso pausado por seguridad", excepcion.getMessage(),
                List.of(), peticion);
        detalle.setProperty("esperaSegundos", excepcion.esperaRestante().toSeconds());
        return detalle;
    }

    /**
     * La cuenta esta inhabilitada por decision de seguridad o de soporte.
     *
     * <p>403 y no 423: no es una pausa que se levante sola, es una negativa mientras alguien
     * no intervenga. Por eso el mensaje dirige a soporte y no invita a reintentar.
     */
    @ExceptionHandler(CuentaInhabilitadaException.class)
    public ProblemDetail alEstarInhabilitada(CuentaInhabilitadaException excepcion,
                                             WebRequest peticion) {
        return problema(HttpStatus.FORBIDDEN, "cuenta-inhabilitada",
                "Tu cuenta esta inhabilitada", excepcion.getMessage(), List.of(), peticion);
    }

    /**
     * Escenario 4 y sesion caducada. Comparten respuesta porque el cliente hace lo mismo en
     * los dos casos —mandar al usuario a iniciar sesion— y porque decirle a quien reutilizo
     * un token que se le detecto solo le informa de que ya no vale la pena insistir con ese.
     * Al titular si se le avisa, por correo.
     */
    @ExceptionHandler({SesionExpiradaException.class,
                       ReutilizacionDeRefreshTokenException.class})
    public ProblemDetail alCaducarLaSesion(RuntimeException excepcion, WebRequest peticion) {
        return problema(HttpStatus.UNAUTHORIZED, "sesion-expirada",
                "Tu sesion ya no es valida",
                "Vuelve a iniciar sesion para continuar.", List.of(), peticion);
    }

    /** Objetos de valor del dominio que rechazan su entrada. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail alRecibirUnValorInvalido(IllegalArgumentException excepcion,
                                                  WebRequest peticion) {
        return problema(HttpStatus.BAD_REQUEST, "validacion",
                "Los datos enviados no son validos", excepcion.getMessage(), List.of(), peticion);
    }

    /**
     * Cualquier fallo no previsto.
     *
     * <p>El detalle que se devuelve es deliberadamente vago. Un mensaje de excepcion real
     * filtra nombres de tablas, versiones de bibliotecas y rutas del servidor, que es
     * material de reconocimiento gratuito para un atacante. La causa completa va al log,
     * que es donde el equipo puede leerla.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail alFallarInesperadamente(Exception excepcion, WebRequest peticion) {
        log.error("Fallo no controlado atendiendo la peticion", excepcion);

        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "error-interno",
                "No pudimos completar la operacion",
                "Vuelve a intentarlo en unos minutos. Si el problema persiste, escribenos.",
                List.of(), peticion);
    }

    private ProblemDetail problema(HttpStatus estado, String tipo, String titulo,
                                   String detalle, List<ErrorDeCampo> errores,
                                   WebRequest peticion) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create(BASE_DE_TIPOS + tipo));
        problema.setTitle(titulo);
        if (detalle != null) {
            problema.setDetail(detalle);
        }
        if (!errores.isEmpty()) {
            problema.setProperty("errores", errores);
        }
        problema.setInstance(URI.create(rutaDe(peticion)));
        return problema;
    }

    private String rutaDe(WebRequest peticion) {
        String descripcion = peticion.getDescription(false);
        return descripcion.startsWith("uri=") ? descripcion.substring(4) : descripcion;
    }

    /** Un campo del formulario y por que no es valido. */
    public record ErrorDeCampo(String campo, String mensaje) {
    }
}
