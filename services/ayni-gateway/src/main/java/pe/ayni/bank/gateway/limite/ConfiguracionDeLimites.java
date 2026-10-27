package pe.ayni.bank.gateway.limite;

import java.net.InetSocketAddress;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ServerWebExchange;

import pe.ayni.bank.gateway.seguridad.FiltroDeAutenticacion;
import reactor.core.publisher.Mono;

/**
 * El limitador y las dos formas de contar: por IP y por titular (AYNI-160, ADR-0035). Las
 * politicas de cada ruta viven en application.yml, junto a la ruta que limitan.
 */
@Configuration
public class ConfiguracionDeLimites {

    /**
     * La IP que anoto Caddy, el ultimo valor de X-Forwarded-For: los anteriores los escribe el
     * cliente y no valen como identidad. Sin la cabecera, la direccion de la conexion.
     */
    private static final XForwardedRemoteAddressResolver RESOLUTOR_DE_IP =
            XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    @Bean
    public LimitadorConRetryAfter limitadorDeTasa(ReactiveStringRedisTemplate redis,
            @Qualifier(RedisRateLimiter.REDIS_SCRIPT_NAME) RedisScript<List<Long>> guion,
            ConfigurationService configuracion) {
        return new LimitadorConRetryAfter(redis, guion, configuracion);
    }

    /** Lo que se hace antes de tener sesion (entrar, registrarse) solo se puede contar por IP. */
    @Bean
    @Primary
    public KeyResolver porIp() {
        return ConfiguracionDeLimites::ipDe;
    }

    /**
     * Lo que mueve dinero se cuenta por titular: varios clientes detras de la misma IP (una
     * oficina, una red movil) no se reparten un mismo cupo. El titular es el que fijo el filtro
     * de autenticacion, nunca uno que mande el cliente; sin el, se cuenta por IP.
     */
    @Bean
    public KeyResolver porUsuario() {
        return intercambio -> Mono.justOrEmpty(
                        intercambio.getRequest().getHeaders().getFirst(FiltroDeAutenticacion.CABECERA_USUARIO))
                .map(usuario -> "usuario:" + usuario)
                .switchIfEmpty(Mono.defer(() -> ipDe(intercambio)));
    }

    static Mono<String> ipDe(ServerWebExchange intercambio) {
        InetSocketAddress direccion = RESOLUTOR_DE_IP.resolve(intercambio);
        if (direccion == null) {
            return Mono.empty();
        }
        String ip = direccion.getAddress() != null ? direccion.getAddress().getHostAddress() : direccion.getHostString();
        return Mono.just("ip:" + ip);
    }
}
