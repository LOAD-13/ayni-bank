package pe.ayni.bank.gateway.limite;

import java.net.InetSocketAddress;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.reactive.ServerHttpRequest;

import reactor.core.publisher.Mono;

import pe.ayni.bank.gateway.seguridad.FiltroDeAutenticacion;

/**
 * Limite de tasa del gateway · AYNI-160, propuesta del taller de seguridad de la semana 8.
 *
 * <p>Los limites por ruta viven en {@code application.yml}, junto a cada ruta: asi se lee de
 * un vistazo que endpoint tiene que limite. Aqui solo se define <em>a quien</em> se cuenta.
 *
 * <p>Si Redis no responde, el limitador deja pasar la peticion: un fallo del contador no
 * debe tumbar el ingreso de todos los clientes. El bloqueo progresivo de identity sigue
 * protegiendo cada cuenta aunque el limite no actue.
 */
@Configuration
public class ConfiguracionDeLimiteDeTasa {

    /**
     * Por direccion IP del cliente. Detras de Caddy la conexion llega desde el proxy, asi
     * que la IP real es la primera de {@code X-Forwarded-For}. Caddy reescribe esa cabecera
     * con la direccion que ve —no confia en la que trae el cliente—, de modo que no se
     * puede falsificar para repartir los intentos entre IP inventadas.
     *
     * <p>Es el resolutor por defecto del filtro: el gateway exige uno solo cuando hay varios.
     */
    @Bean
    @Primary
    public KeyResolver porIp() {
        return intercambio -> Mono.just(ipDe(intercambio.getRequest()));
    }

    /**
     * Por titular autenticado, para las operaciones con dinero. La cabecera la pone el
     * gateway tras validar el JWT y la borra de toda peticion entrante, asi que es fiable.
     */
    @Bean
    public KeyResolver porUsuario() {
        return intercambio -> Mono.just(Optional
                .ofNullable(intercambio.getRequest().getHeaders()
                        .getFirst(FiltroDeAutenticacion.CABECERA_USUARIO))
                .map(usuario -> "usuario:" + usuario)
                .orElseGet(() -> ipDe(intercambio.getRequest())));
    }

    @Bean
    public CabeceraRetryAfter cabeceraRetryAfter() {
        return new CabeceraRetryAfter();
    }

    static String ipDe(ServerHttpRequest peticion) {
        String reenviada = peticion.getHeaders().getFirst("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return "ip:" + reenviada.split(",")[0].trim();
        }
        return "ip:" + Optional.ofNullable(peticion.getRemoteAddress())
                .map(InetSocketAddress::getHostString)
                .orElse("desconocida");
    }
}
