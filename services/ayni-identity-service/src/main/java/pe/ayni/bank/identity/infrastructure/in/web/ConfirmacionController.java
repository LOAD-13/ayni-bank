package pe.ayni.bank.identity.infrastructure.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.ayni.bank.identity.domain.model.HuellaDeCliente;
import pe.ayni.bank.identity.domain.model.OperacionAConfirmar;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.in.ConfirmarOperacionUseCase;
import pe.ayni.bank.identity.domain.model.ConfirmacionEmitida;
import pe.ayni.bank.identity.domain.model.ConfirmacionIniciada;

/**
 * HU-07 · Confirmar una operacion con el segundo factor (ADR-0031).
 *
 * <p>Ruta protegida: el gateway valida el JWT y pone la identidad en {@value #CABECERA_USUARIO}.
 * El titular nunca viaja en la URL ni en el cuerpo.
 */
@RestController
@RequestMapping("/api/v1/confirmaciones")
public class ConfirmacionController {

    static final String CABECERA_USUARIO = "X-Ayni-Usuario";

    private final ConfirmarOperacionUseCase confirmar;

    public ConfirmacionController(ConfirmarOperacionUseCase confirmar) {
        this.confirmar = confirmar;
    }

    @PostMapping
    public ResponseEntity<ConfirmacionIniciadaDto> iniciar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @Valid @RequestBody SolicitudDeConfirmacionDeOperacionDto cuerpo,
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente) {
        ConfirmacionIniciada iniciada = confirmar.iniciar(usuarioId,
                new OperacionAConfirmar(cuerpo.destino(), new BigDecimal(cuerpo.importe()), cuerpo.moneda(),
                        cuerpo.claveIdempotencia()),
                new HuellaDeCliente(ipDe(peticion), agente));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ConfirmacionIniciadaDto(
                iniciada.confirmacionId(), iniciada.metodo(), iniciada.expiraEn()));
    }

    @PostMapping("/{confirmacionId}/verificacion")
    public ResponseEntity<TokenDeConfirmacionDto> verificar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @PathVariable UUID confirmacionId,
            @Valid @RequestBody CodigoDeConfirmacionDto cuerpo,
            HttpServletRequest peticion,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String agente) {
        ConfirmacionEmitida emitida = confirmar.verificar(usuarioId, confirmacionId, cuerpo.codigo(),
                new HuellaDeCliente(ipDe(peticion), agente));
        return ResponseEntity.ok(new TokenDeConfirmacionDto(emitida.token(), emitida.expiraEn()));
    }

    private static String ipDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }

    /** La operacion que se va a confirmar, en los mismos terminos que la transferencia. */
    public record SolicitudDeConfirmacionDeOperacionDto(
            @NotBlank @Size(max = 32) String destino,
            @NotBlank @Pattern(regexp = "^\\d{1,7}(\\.\\d{1,2})?$", message = "El importe no es valido.") String importe,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda no es valida.") String moneda,
            @NotNull UUID claveIdempotencia) {
    }

    public record CodigoDeConfirmacionDto(@NotBlank @Size(max = 12) String codigo) {

        @Override
        public String toString() {
            return "CodigoDeConfirmacionDto[oculto]";
        }
    }

    public record ConfirmacionIniciadaDto(UUID confirmacionId, TipoDeSegundoFactor metodo, Instant expiraEn) {
    }

    public record TokenDeConfirmacionDto(String token, Instant expiraEn) {

        @Override
        public String toString() {
            return "TokenDeConfirmacionDto[oculto]";
        }
    }
}
