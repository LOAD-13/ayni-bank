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
