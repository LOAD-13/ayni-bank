package pe.ayni.bank.gateway.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import reactor.test.StepVerifier;

class LimitadorConRetryAfterTest {

    private static Map<String, String> cabeceras(long reposicion, long pedidas, long quedan) {
        return Map.of(
                RedisRateLimiter.REPLENISH_RATE_HEADER, String.valueOf(reposicion),
                RedisRateLimiter.REQUESTED_TOKENS_HEADER, String.valueOf(pedidas),
                RedisRateLimiter.REMAINING_HEADER, String.valueOf(quedan));
    }

    @Test
    @DisplayName("Retry-After es lo que tarda el cubo en reponer las fichas que faltan")
    void loQueFaltaEntreLaReposicion() {
        assertThat(LimitadorConRetryAfter.segundosDeEspera(cabeceras(1, 12, 0))).isEqualTo(12);
        assertThat(LimitadorConRetryAfter.segundosDeEspera(cabeceras(1, 360, 100))).isEqualTo(260);
    }

    @Test
    @DisplayName("Se redondea hacia arriba: reintentar antes de tiempo volveria a dar 429")
    void redondeaHaciaArriba() {
        assertThat(LimitadorConRetryAfter.segundosDeEspera(cabeceras(2, 3, 0))).isEqualTo(2);
    }

    @Test
    @DisplayName("Sin cabeceras del limitador (o con valores raros) se espera 1 s, nunca 0 ni negativo")
    void sinDatosUnSegundo() {
        assertThat(LimitadorConRetryAfter.segundosDeEspera(Map.of())).isEqualTo(1);
        assertThat(LimitadorConRetryAfter.segundosDeEspera(cabeceras(1, 12, -1))).isEqualTo(12);
        assertThat(LimitadorConRetryAfter.segundosDeEspera(cabeceras(1, 5, 9))).isEqualTo(1);
        assertThat(LimitadorConRetryAfter.segundosDeEspera(
                Map.of(RedisRateLimiter.REQUESTED_TOKENS_HEADER, "doce"))).isEqualTo(1);
    }

    @Test
    @DisplayName("Con Redis caido el limitador deja pasar: sin contador no se bloquea la banca entera")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void redisCaidoDejaPasar() {
        LettuceConnectionFactory conexion = new LettuceConnectionFactory("localhost", 1);
        conexion.afterPropertiesSet();
        DefaultRedisScript guion = new DefaultRedisScript<>();
        guion.setScriptSource(new ResourceScriptSource(new ClassPathResource("META-INF/scripts/request_rate_limiter.lua")));
        guion.setResultType(List.class);
        LimitadorConRetryAfter limitador = new LimitadorConRetryAfter(new ReactiveStringRedisTemplate(conexion), guion, null);
        limitador.getConfig().put("sesion", new RedisRateLimiter.Config()
                .setReplenishRate(1).setBurstCapacity(60).setRequestedTokens(12));

        try {
            StepVerifier.create(limitador.isAllowed("sesion", "ip:203.0.113.1"))
                    .assertNext(respuesta -> assertThat(respuesta.isAllowed()).isTrue())
                    .verifyComplete();
        } finally {
            conexion.destroy();
        }
    }
}
