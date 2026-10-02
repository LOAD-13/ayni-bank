package pe.ayni.bank.gateway.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Validacion centralizada del JWT en el gateway (AYNI-122).
 *
 * <p>Antes cada servicio tenia que acordarse de validar el token, y el que se olvidaba
 * quedaba abierto. Ahora las rutas protegidas no llegan a ningun servicio sin un token
 * valido: responden 401 aqui mismo.
 *
 * <p>Si el token es valido, el gateway reenvia la peticion con la cabecera
 * {@value #CABECERA_USUARIO} igual al {@code sub} del token. Los servicios toman la
 * identidad de esa cabecera y nunca de la URL ni del cuerpo, de modo que nadie puede
 * consultar ni mover el dinero de otro cambiando un identificador. La cabecera se borra
 * siempre de la peticion entrante: un cliente no puede fabricarsela.
 */
public class FiltroDeAutenticacion implements GlobalFilter, Ordered {

    public static final String CABECERA_USUARIO = "X-Ayni-Usuario";
    private static final String PREFIJO = "Bearer ";
    private static final byte[] CUERPO_401 = ("{\"type\":\"about:blank\",\"title\":\"Sesion requerida\","
            + "\"status\":401,\"detail\":\"Inicia sesion para continuar.\"}")
            .getBytes(StandardCharsets.UTF_8);

    private final VerificadorDeTokens verificador;
    private final List<String> rutasProtegidas;
    private final AntPathMatcher comparador = new AntPathMatcher();

    public FiltroDeAutenticacion(VerificadorDeTokens verificador, List<String> rutasProtegidas) {
        this.verificador = verificador;
        this.rutasProtegidas = List.copyOf(rutasProtegidas);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
        ServerHttpRequest peticion = intercambio.getRequest().mutate()
                .headers(cabeceras -> cabeceras.remove(CABECERA_USUARIO))
                .build();

        if (!esProtegida(peticion)) {
            return cadena.filter(intercambio.mutate().request(peticion).build());
        }

        Optional<String> usuario = tokenDe(peticion).flatMap(verificador::usuarioDe);
        if (usuario.isEmpty()) {
            return rechazar(intercambio.getResponse());
        }

        ServerHttpRequest autenticada = peticion.mutate()
                .header(CABECERA_USUARIO, usuario.get())
                .build();
        return cadena.filter(intercambio.mutate().request(autenticada).build());
    }

    /** Antes que el enrutado, para que una peticion sin token no llegue a ningun servicio. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private boolean esProtegida(ServerHttpRequest peticion) {
        // El preflight CORS no lleva credenciales por definicion: exigirselas lo rompe.
        if (HttpMethod.OPTIONS.equals(peticion.getMethod())) {
            return false;
        }
        String ruta = peticion.getPath().value();
        return rutasProtegidas.stream().anyMatch(patron -> comparador.match(patron, ruta));
    }

    private static Optional<String> tokenDe(ServerHttpRequest peticion) {
        String cabecera = peticion.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            return Optional.empty();
        }
        String token = cabecera.substring(PREFIJO.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /** 401 con cuerpo RFC 7807, igual que el resto de errores de la API. */
    private static Mono<Void> rechazar(ServerHttpResponse respuesta) {
        respuesta.setStatusCode(HttpStatus.UNAUTHORIZED);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        respuesta.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        DataBuffer buffer = respuesta.bufferFactory().wrap(CUERPO_401);
        return respuesta.writeWith(Mono.just(buffer));
    }
}
