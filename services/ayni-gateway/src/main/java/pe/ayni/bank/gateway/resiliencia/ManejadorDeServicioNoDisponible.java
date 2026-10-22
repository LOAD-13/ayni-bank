package pe.ayni.bank.gateway.resiliencia;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;

/**
 * Un servicio de detras que no acepta conexiones (apagandose, reiniciandose tras pasar su techo de
 * memoria, o caido) responde 503 con {@code Retry-After}, y no el 500 por defecto del gateway.
 *
 * <p>500 dice «fallo nuestro e imprevisto»; 503 dice «ahora no, reintenta en unos segundos», que es
 * lo que de verdad pasa, y es lo que un cliente (y la web) sabe tratar: mostrar «estamos ocupados» y
 * reintentar, en vez de un error. ISO/IEC 25010, coexistencia (ADR-0034). Solo se ocupa del rechazo
 * de la conexion: cualquier otro error sigue su camino normal.
 */
@Component
@Order(-2)   // antes que el manejador de errores de Spring Boot (-1)
public class ManejadorDeServicioNoDisponible implements WebExceptionHandler {

    static final String REINTENTAR_EN_SEGUNDOS = "5";

    private static final byte[] CUERPO = ("{\"type\":\"https://ayni.pe/problemas/servicio-no-disponible\","
            + "\"title\":\"Servicio no disponible\",\"status\":503,"
            + "\"detail\":\"El servicio esta ocupado o reiniciandose. Vuelve a intentarlo en unos segundos.\","
            + "\"codigo\":\"SERVICIO_NO_DISPONIBLE\"}").getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> handle(ServerWebExchange intercambio, Throwable error) {
        if (!esConexionRechazada(error) || intercambio.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        var respuesta = intercambio.getResponse();
        respuesta.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        respuesta.getHeaders().set("Retry-After", REINTENTAR_EN_SEGUNDOS);
        DataBuffer cuerpo = respuesta.bufferFactory().wrap(CUERPO);
        return respuesta.writeWith(Mono.just(cuerpo));
    }

    /** Netty envuelve el rechazo en su propia subclase de ConnectException; se busca en toda la cadena. */
    static boolean esConexionRechazada(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConnectException) {
                return true;
            }
        }
        return false;
    }
}
