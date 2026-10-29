package pe.ayni.bank.gateway.limite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;

import reactor.core.publisher.Mono;

/**
 * El limitador de Redis de Spring Cloud Gateway, con {@code Retry-After} en el 429 (AYNI-160).
 *
 * <p>El de serie responde 429 sin decir cuanto esperar, y un cliente que reintenta a ciegas
 * solo alarga su bloqueo. El calculo sale de las cabeceras que el propio limitador ya
 * devuelve, asi que no hay una segunda fuente de verdad sobre la politica de cada ruta.
 *
 * <p>Si Redis no responde, el limitador de serie deja pasar la peticion, y aqui se mantiene:
 * perder el limite unos minutos es preferible a dejar a todos los clientes sin banca.
 * Ver ADR-0035.
 */
public class LimitadorConRetryAfter extends RedisRateLimiter {

    public LimitadorConRetryAfter(ReactiveStringRedisTemplate redis, RedisScript<List<Long>> guion,
            ConfigurationService configuracion) {
        super(redis, guion, configuracion);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        return super.isAllowed(routeId, id)
                .map(respuesta -> respuesta.isAllowed() ? respuesta : conRetryAfter(respuesta));
    }

    private static Response conRetryAfter(Response respuesta) {
        Map<String, String> cabeceras = new HashMap<>(respuesta.getHeaders());
        cabeceras.put(HttpHeaders.RETRY_AFTER, String.valueOf(segundosDeEspera(cabeceras)));
        return new Response(false, cabeceras);
    }

    /**
     * Lo que tarda el cubo en reponer las fichas que le faltan a una peticion, redondeado
     * hacia arriba: reintentar un segundo antes volveria a dar 429. Nunca menos de 1.
     */
    static long segundosDeEspera(Map<String, String> cabeceras) {
        long pedidas = numero(cabeceras, REQUESTED_TOKENS_HEADER);
        long quedan = Math.max(0, numero(cabeceras, REMAINING_HEADER));
        long reposicion = numero(cabeceras, REPLENISH_RATE_HEADER);
        if (pedidas <= 0 || reposicion <= 0) {
            return 1;
        }
        return Math.max(1, Math.ceilDiv(pedidas - quedan, reposicion));
    }

    private static long numero(Map<String, String> cabeceras, String nombre) {
        try {
            return Long.parseLong(cabeceras.getOrDefault(nombre, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
