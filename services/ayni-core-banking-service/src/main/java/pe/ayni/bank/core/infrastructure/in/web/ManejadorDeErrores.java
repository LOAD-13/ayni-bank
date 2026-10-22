package pe.ayni.bank.core.infrastructure.in.web;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Peticiones mal formadas: siempre 400 en {@code application/problem+json} (RFC 7807), el mismo
 * formato que usa identity-service, con el codigo {@code SOLICITUD_INVALIDA}.
 *
 * <p>Sin este manejador, Spring respondia 400 con su cuerpo generico, distinto entre errores y sin
 * decir que campo fallaba. Ningun mensaje repite el valor recibido ni el texto de la excepcion:
 * el de Jackson cita el JSON de entrada y las clases internas, que no le sirven a quien llama y
 * si a quien sondea la API. Se nombra el campo y que se esperaba. ISO/IEC 25010, interoperabilidad.
 *
 * <p>Los manejadores propios de cada controlador (rechazo de la operacion, cabecera de identidad
 * ausente, confirmacion requerida) tienen prioridad sobre estos.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final URI TIPO = URI.create("https://ayni.pe/problemas/solicitud-invalida");

    record ErrorDeCampo(String campo, String mensaje) {
    }

    /** Las anotaciones del DTO no se cumplen: importe con formato no valido, campo obligatorio vacio. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> alFallarLaValidacion(MethodArgumentNotValidException excepcion) {
        List<ErrorDeCampo> errores = excepcion.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorDeCampo(e.getField(), e.getDefaultMessage()))
                // Orden estable: dos peticiones iguales devuelven los errores en el mismo orden.
                .sorted(Comparator.comparing(ErrorDeCampo::campo))
                .toList();
        return invalida("Los datos enviados no son validos.", Map.of("errores", errores));
    }

    /** El cuerpo no es JSON, o un campo trae un tipo que no corresponde. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> alNoPoderLeerElCuerpo() {
        return invalida("El cuerpo de la peticion no es un JSON valido para esta operacion.", Map.of());
    }

    /** Un parametro de ruta o una cabecera con formato equivocado, como un identificador que no es UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> alNoTenerElFormato(MethodArgumentTypeMismatchException excepcion) {
        return invalida("El valor de «" + excepcion.getName() + "» no tiene el formato esperado.", Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> alFaltarUnParametro(MissingServletRequestParameterException excepcion) {
        return invalida("Falta el parametro «" + excepcion.getParameterName() + "».", Map.of());
    }

    private static ResponseEntity<ProblemDetail> invalida(String detalle, Map<String, Object> extras) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problema.setType(TIPO);
        problema.setTitle("Solicitud no valida");
        problema.setProperty("codigo", "SOLICITUD_INVALIDA");
        extras.forEach(problema::setProperty);
        return ResponseEntity.badRequest().body(problema);
    }
}
