package pe.ayni.bank.gateway.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.CacheControl;
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
 * siempre de la peticion entrante: un cliente no puede fabricarsela. Lo mismo con
 * {@value #CABECERA_PERMISOS}, que lleva los permisos del token.
 *
 * <p>Los permisos del token deciden por ruta (AYNI-281, {@link PermisosPorRuta}): una sesion
 * valida sin el permiso que la ruta exige recibe 403 aqui mismo.
 */
public class FiltroDeAutenticacion implements GlobalFilter, Ordered {

    public static final String CABECERA_USUARIO = "X-Ayni-Usuario";
    /** Los permisos del token, como informacion para los servicios. Se borra de lo que llega. */
    public static final String CABECERA_PERMISOS = "X-Ayni-Permisos";
    private static final String PREFIJO = "Bearer ";
    private static final byte[] CUERPO_401 = ("{\"type\":\"about:blank\",\"title\":\"Sesion requerida\","
            + "\"status\":401,\"detail\":\"Inicia sesion para continuar.\"}")
            .getBytes(StandardCharsets.UTF_8);

    private static final byte[] CUERPO_403 = ("{\"type\":\"about:blank\",\"title\":\"Permiso insuficiente\","
            + "\"status\":403,\"detail\":\"Tu rol no permite esta operacion.\"}")
            .getBytes(StandardCharsets.UTF_8);

    private final VerificadorDeTokens verificador;
    private final List<String> rutasProtegidas;
    private final PermisosPorRuta permisosPorRuta;
    private final AntPathMatcher comparador = new AntPathMatcher();

    public FiltroDeAutenticacion(VerificadorDeTokens verificador, List<String> rutasProtegidas) {
        this(verificador, rutasProtegidas, new PermisosPorRuta(List.of()));
    }

    public FiltroDeAutenticacion(VerificadorDeTokens verificador, List<String> rutasProtegidas,
                                 PermisosPorRuta permisosPorRuta) {
        this.verificador = verificador;
        this.rutasProtegidas = List.copyOf(rutasProtegidas);
        this.permisosPorRuta = permisosPorRuta;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange intercambio, GatewayFilterChain cadena) {
        aplicarCabecerasDeSeguridad(intercambio.getResponse());

        ServerHttpRequest peticion = intercambio.getRequest().mutate()
                .headers(cabeceras -> {
                    cabeceras.remove(CABECERA_USUARIO);
                    cabeceras.remove(CABECERA_PERMISOS);
                })
                .build();

        if (!esProtegida(peticion)) {
            return cadena.filter(intercambio.mutate().request(peticion).build());
        }
        // Lo que va detras de la sesion son datos del cliente (saldo, numero de cuenta,
        // movimientos): no se guarda en ninguna cache, ni del navegador ni intermedia
        // (AYNI-313). Si el servicio de destino manda su propia Cache-Control, prevalece.
        intercambio.getResponse().getHeaders().setCacheControl(CacheControl.noStore());

        Optional<VerificadorDeTokens.Identidad> identidad = tokenDe(peticion).flatMap(verificador::identidadDe);
        if (identidad.isEmpty()) {
            return rechazar(intercambio.getResponse());
        }
        Optional<String> exigido = permisosPorRuta.exigidoPara(peticion.getMethod(), peticion.getPath().value());
        if (exigido.isPresent() && !identidad.get().permisos().contains(exigido.get())) {
            return prohibir(intercambio.getResponse());
        }

        ServerHttpRequest autenticada = peticion.mutate()
                .header(CABECERA_USUARIO, identidad.get().usuario())
                .header(CABECERA_PERMISOS, String.join(",", new TreeSet<>(identidad.get().permisos())))
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

    /** 403: la sesion es valida, pero el rol del usuario no permite esta operacion. */
    private static Mono<Void> prohibir(ServerHttpResponse respuesta) {
        respuesta.setStatusCode(HttpStatus.FORBIDDEN);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = respuesta.bufferFactory().wrap(CUERPO_403);
        return respuesta.writeWith(Mono.just(buffer));
    }

    /** OWASP A05: cabeceras defensivas en todas las respuestas que atraviesan el gateway. */
    private static void aplicarCabecerasDeSeguridad(ServerHttpResponse respuesta) {
        HttpHeaders cabeceras = respuesta.getHeaders();
        cabeceras.set("X-Content-Type-Options", "nosniff");
        cabeceras.set("X-Frame-Options", "DENY");
        cabeceras.set("Referrer-Policy", "strict-origin-when-cross-origin");
        cabeceras.set("Permissions-Policy", "camera=(self), microphone=(), geolocation=()");
    }
}
