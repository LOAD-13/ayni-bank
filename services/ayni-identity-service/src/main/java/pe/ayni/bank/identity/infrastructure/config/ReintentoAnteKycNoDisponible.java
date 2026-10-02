package pe.ayni.bank.identity.infrastructure.config;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Predicate;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Que fallos de kyc-service merecen reintento (ADR-0020, ADR-0028).
 *
 * <p>Este predicado decide solo: Resilience4j combina {@code retry-exceptions} y
 * {@code retry-exception-predicate} con un O logico, asi que una lista de excepciones en
 * application.yml anularia la exclusion de los timeouts (se comprobo en
 * AdaptadorVerificadorKycTest).
 *
 * <p>Se reintentan los 5xx y los errores de E/S, salvo los timeouts de lectura: reintentar una
 * llamada que ya espero 10 s triplica la espera del solicitante (hasta ~31 s) sin hacerla mas
 * probable de responder. Una conexion rechazada, en cambio, falla al instante y un reintento
 * corto si puede servir. Un 4xx nunca: reintentar una peticion mal formada no la arregla.
 */
public class ReintentoAnteKycNoDisponible implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable fallo) {
        if (fallo instanceof HttpServerErrorException) {
            return true;
        }
        return fallo instanceof ResourceAccessException && !esUnTimeout(fallo);
    }

    private static boolean esUnTimeout(Throwable fallo) {
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            if (causa instanceof SocketTimeoutException || causa instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
