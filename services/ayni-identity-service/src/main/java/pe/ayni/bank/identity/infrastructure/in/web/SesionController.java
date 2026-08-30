package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import pe.ayni.bank.identity.domain.model.CodigoTotp;
import pe.ayni.bank.identity.domain.model.ComandoDeIngreso;
import pe.ayni.bank.identity.domain.model.ComandoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.DesafioAbierto;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.SesionExpiradaException;
import pe.ayni.bank.identity.domain.model.SesionIniciada;
import pe.ayni.bank.identity.domain.port.in.IniciarSesionUseCase;

/** HU-04 · Inicio de sesion seguro con segundo factor. */
@RestController
@RequestMapping("/api/v1/sesion")
public class SesionController {

    /**
     * Nombre de la cookie del token de renovacion en produccion.
     *
     * <p>El prefijo {@code __Host-} no es decorativo: el navegador solo acepta una cookie
     * con ese nombre si viaja por HTTPS, tiene {@code Path=/} y <strong>no</strong> declara
     * dominio. Eso impide que un subdominio comprometido escriba la cookie de sesion del
     * dominio principal, que es un ataque real y silencioso.
     */
    private static final String COOKIE_SEGURA = "__Host-ayni-renovacion";

    /**
     * El mismo nombre sin prefijo, para desarrollo.
     *
     * <p>Y no es una comodidad: el prefijo {@code __Host-} <strong>exige</strong> el
     * atributo {@code Secure}, asi que en local, sobre HTTP, el navegador descarta la
     * cookie sin decir nada. La peticion responde 200, el ingreso parece correcto y la
     * primera renovacion falla sin motivo aparente. Cambiar el nombre junto con el atributo
     * evita ese fallo silencioso.
     */
    private static final String COOKIE_LOCAL = "ayni-renovacion";

    private final IniciarSesionUseCase iniciarSesion;
    private final boolean cookieSegura;
    private final String nombreDeLaCookie;

    public SesionController(IniciarSesionUseCase iniciarSesion,
                            @Value("${ayni.cookies.seguras:true}") boolean cookieSegura) {
        this.iniciarSesion = iniciarSesion;
        this.cookieSegura = cookieSegura;
        this.nombreDeLaCookie = cookieSegura ? COOKIE_SEGURA : COOKIE_LOCAL;
    }

    /** Paso 1: correo y contrasena. Devuelve el desafio del segundo factor. */
    @PostMapping
    public ResponseEntity<DesafioDto> presentarCredenciales(
            @Valid @RequestBody CredencialesDto credenciales,
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente) {

        DesafioAbierto desafio = iniciarSesion.presentarCredenciales(new ComandoDeIngreso(
                credenciales.correo(), credenciales.contrasena(),
                new HuellaDeCliente(ipDe(peticion), agente)));

        return ResponseEntity.ok(DesafioDto.desde(desafio));
    }

    /** Paso 2: el codigo de la aplicacion de autenticacion. */
    @PostMapping("/segundo-factor")
    public ResponseEntity<SesionDto> verificarSegundoFactor(
            @Valid @RequestBody SegundoFactorDto cuerpo,
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente) {

        SesionIniciada sesion = iniciarSesion.verificarSegundoFactor(
                new ComandoDeSegundoFactor(
                        UUID.fromString(cuerpo.desafioId()),
                        new CodigoTotp(cuerpo.codigo()),
                        new HuellaDeCliente(ipDe(peticion), agente)));

        return responderCon(sesion);
    }

    /** Renueva la sesion rotando el token de la cookie. */
    @PostMapping("/renovacion")
    public ResponseEntity<SesionDto> renovar(
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente) {

        String token = tokenDeLaCookie(peticion);
        if (token == null || token.isBlank()) {
            throw new SesionExpiradaException();
        }

        return responderCon(iniciarSesion.renovar(
                token, new HuellaDeCliente(ipDe(peticion), agente)));
    }

    /**
     * El token de renovacion sale en una cookie y <strong>no</strong> en el cuerpo.
     *
     * <p>Con {@code HttpOnly}, JavaScript no puede leerla, asi que un XSS en la aplicacion
     * web no se lleva la sesion de siete dias. El token de acceso, en cambio, si va en el
     * cuerpo: dura quince minutos y el cliente necesita ponerlo en la cabecera
     * {@code Authorization}. Es un reparto deliberado: lo de vida larga donde el script no
     * llega, lo de vida corta donde hace falta.
     *
     * <p>{@code SameSite=Strict} porque esta cookie solo se usa en peticiones que nacen en
     * la propia aplicacion; con {@code Lax} viajaria tambien en navegaciones desde otros
     * sitios, que es la puerta de un CSRF.
     */
    private ResponseEntity<SesionDto> responderCon(SesionIniciada sesion) {
        ResponseCookie cookie = ResponseCookie.from(
                        nombreDeLaCookie, sesion.tokenDeRenovacion())
                .httpOnly(true)
                .secure(cookieSegura)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.between(Instant.now(), sesion.laRenovacionExpiraEn()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(SesionDto.desde(sesion));
    }

    /**
     * Lee la cookie por su nombre, que depende del entorno.
     *
     * <p>No se puede usar {@code @CookieValue}: esa anotacion necesita el nombre en tiempo
     * de compilacion, y aqui se decide al arrancar segun si las cookies van seguras.
     */
    private String tokenDeLaCookie(HttpServletRequest peticion) {
        if (peticion.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : peticion.getCookies()) {
            if (nombreDeLaCookie.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * La IP del cliente, no la del gateway.
     *
     * <p>Todo pasa por {@code ayni-gateway}, asi que {@code getRemoteAddr()} devolveria
     * siempre la misma direccion interna y la pista de auditoria no serviria para nada. Se
     * lee el primer valor de {@code X-Forwarded-For}, que es el que anade el proxy mas
     * cercano al cliente.
     *
     * <p>Esa cabecera la puede falsificar quien llame directamente al servicio; no se
     * puede, porque el servicio no esta publicado fuera de la red interna del compose. Si
     * algun dia lo estuviera, esto tendria que validarse contra una lista de proxies de
     * confianza.
     */
    private static String ipDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }
}
