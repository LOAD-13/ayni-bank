package pe.ayni.bank.gateway.limite;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Anade {@code Retry-After} a toda respuesta 429.
 *
 * <p>El limitador de Spring Cloud Gateway responde 429 con sus cabeceras
 * {@code X-RateLimit-*}, pero sin {@code Retry-After}, que es la que entienden los
 * navegadores y los clientes HTTP para saber cuanto esperar. Todos los limites son por
 * minuto u hora, asi que un minuto es la espera minima util.
 */
public class CabeceraRetryAfter implements WebFilter {

    static final String SEGUNDOS = "60";

    @Override
    public Mono<Void> filter(ServerWebExchange intercambio, WebFilterChain cadena) {
        intercambio.getResponse().beforeCommit(() -> {
            if (HttpStatus.TOO_MANY_REQUESTS.equals(intercambio.getResponse().getStatusCode())) {
                intercambio.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, SEGUNDOS);
            }
            return Mono.empty();
        });
        return cadena.filter(intercambio);
    }
}
