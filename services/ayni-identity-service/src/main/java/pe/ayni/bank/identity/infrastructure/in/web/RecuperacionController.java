package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.port.in.RecuperarContrasenaUseCase;

/** HU-21 · Recuperacion de la contrasena. Ver ADR-0030. */
@RestController
@RequestMapping("/api/v1/recuperacion")
public class RecuperacionController {

    /** El mismo texto para todos: la respuesta no dice si la cuenta existe (ADR-0008). */
    static final String MENSAJE =
            "Si el correo corresponde a una cuenta de Ayni, te enviamos un enlace para cambiar tu contrasena.";

    private final RecuperarContrasenaUseCase recuperar;
    private final Duration duracionMinima;

    public RecuperacionController(RecuperarContrasenaUseCase recuperar,
                                  @Value("${ayni.recuperacion.duracion-minima:400ms}") Duration duracionMinima) {
        this.recuperar = recuperar;
        this.duracionMinima = duracionMinima;
    }

    /**
     * Pide el enlace. Responde 202 con el mismo cuerpo exista o no la cuenta, y nunca antes
     * de {@code duracionMinima}: buscar la cuenta, guardar el token y anotar la auditoria
     * cuestan distinto en cada caso, y el relleno borra esa diferencia del cronometro.
     */
    @PostMapping
    public ResponseEntity<RespuestaDeRecuperacionDto> solicitar(
            @Valid @RequestBody SolicitudDeRecuperacionDto solicitud,
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente)
            throws InterruptedException {

        long inicio = System.nanoTime();
        recuperar.solicitar(solicitud.correo(), new HuellaDeCliente(ipDe(peticion), agente));
        esperarHastaLaDuracionMinima(inicio);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RespuestaDeRecuperacionDto(MENSAJE));
    }

    /** 204 si el enlace sirve; 410 si no (lo traduce {@link ManejadorDeErrores}). */
    @PostMapping("/validacion")
    public ResponseEntity<Void> validar(@Valid @RequestBody EnlaceDeRecuperacionDto enlace) {
        recuperar.validar(enlace.token());
        return ResponseEntity.noContent().build();
    }

    /** Fija la contrasena nueva. 204; 400 si incumple la politica; 410 si el enlace no sirve. */
    @PostMapping("/confirmacion")
    public ResponseEntity<Void> restablecer(
            @Valid @RequestBody RestablecimientoDeContrasenaDto cuerpo,
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente) {

        recuperar.restablecer(cuerpo.token(), cuerpo.contrasenaNueva(),
                new HuellaDeCliente(ipDe(peticion), agente));
        return ResponseEntity.noContent().build();
    }

    private void esperarHastaLaDuracionMinima(long inicio) throws InterruptedException {
        long restante = duracionMinima.toNanos() - (System.nanoTime() - inicio);
        if (restante > 0) {
            Thread.sleep(Duration.ofNanos(restante));
        }
    }

    private static String ipDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }

    /** Cuerpo de la respuesta 202, identico para todos. */
    public record RespuestaDeRecuperacionDto(String mensaje) {
    }
}
