package pe.ayni.bank.core.infrastructure.interbancaria;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.application.interbancaria.ValidadorCci;
import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;

/**
 * HU-11 · Transferencia interbancaria por CCI y beneficiarios frecuentes.
 *
 * <p>Como en el resto de la banca, <strong>la identidad sale de la cabecera
 * {@value #CABECERA_USUARIO}</strong>, que pone el gateway despues de validar el JWT, y nunca de
 * la URL ni del cuerpo: no se puede operar sobre la cuenta de otro porque no hay donde escribir
 * su identificador. La clave de idempotencia y la confirmacion con segundo factor viajan en
 * cabeceras, igual que en {@code POST /api/v1/transferencias}.
 */
@RestController
@RequestMapping("/api/v1/interbancarias")
public class InterbancariaController {

    static final String CABECERA_USUARIO = "X-Ayni-Usuario";
    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";
    static final String CABECERA_CONFIRMACION = "X-Ayni-Confirmacion";
    /** El importe llega como texto: un numero JSON pasaria por coma flotante en el cliente. */
    private static final String IMPORTE = "^\\d{1,7}(\\.\\d{1,2})?$";

    private final TransferenciaInterbancariaService service;

    public InterbancariaController(TransferenciaInterbancariaService service) {
        this.service = service;
    }

    public record Solicitud(
            @NotBlank String cci,
            @NotBlank @Pattern(regexp = IMPORTE, message = "Importe no valido") String importe,
            @Size(max = 140) String concepto) {
    }

    public record NuevoBeneficiario(@NotBlank @Size(max = 80) String alias, @NotBlank String cci) {
    }

    /** Validacion en vivo del CCI mientras se escribe; no toca datos de nadie. */
    @GetMapping("/validar-cci/{cci}")
    public boolean validar(@PathVariable String cci) {
        return ValidadorCci.esValido(cci);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransferenciaInterbancariaService.Resultado iniciar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @RequestHeader(CABECERA_IDEMPOTENCIA) UUID clave,
            @RequestHeader(value = CABECERA_CONFIRMACION, required = false) String confirmacion,
            @Valid @RequestBody Solicitud solicitud) {
        var resultado = service.iniciar(new TransferenciaInterbancariaService.Solicitud(usuarioId, clave,
                solicitud.cci(), new BigDecimal(solicitud.importe()), solicitud.concepto()), confirmacion);
        service.despachar(resultado.id());
        return service.consultar(resultado.id(), usuarioId);
    }

    @GetMapping("/{id}")
    public TransferenciaInterbancariaService.Resultado consultar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId, @PathVariable UUID id) {
        return service.consultar(id, usuarioId);
    }

    @GetMapping("/beneficiarios")
    public List<TransferenciaInterbancariaService.Beneficiario> beneficiarios(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        return service.beneficiarios(usuarioId);
    }

    @PostMapping("/beneficiarios")
    public TransferenciaInterbancariaService.Beneficiario crearBeneficiario(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId, @Valid @RequestBody NuevoBeneficiario nuevo) {
        return service.guardarBeneficiario(usuarioId, nuevo.alias(), nuevo.cci());
    }

    @DeleteMapping("/beneficiarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarBeneficiario(@RequestHeader(CABECERA_USUARIO) UUID usuarioId, @PathVariable UUID id) {
        service.borrarBeneficiario(usuarioId, id);
    }

    // ── Errores: problem+json con un detalle que dice que hacer ──────────────────────

    @ExceptionHandler(RechazoInterbancarioException.class)
    public ResponseEntity<ProblemDetail> alRechazar(RechazoInterbancarioException rechazo) {
        var motivo = rechazo.motivo();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(motivo.estado(), motivo.detalle());
        problema.setTitle("Transferencia no realizada");
        problema.setProperty("codigo", motivo.name());
        return ResponseEntity.status(motivo.estado()).body(problema);
    }

    /** 403: falta confirmar con el segundo factor; se reintenta con la misma clave. */
    @ExceptionHandler(ConfirmacionInvalidaException.class)
    public ResponseEntity<ProblemDetail> alFaltarConfirmacion(ConfirmacionInvalidaException falta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, falta.getMessage());
        problema.setTitle("Confirmacion requerida");
        problema.setProperty("codigo", "CONFIRMACION_REQUERIDA");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema);
    }

    @ExceptionHandler(ClaveDeIdempotenciaReutilizadaException.class)
    public ResponseEntity<ProblemDetail> alReutilizarClave() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Esa clave de idempotencia ya se uso en otra operacion.");
        problema.setTitle("Operacion duplicada");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }

    /** Sin la cabecera de usuario la peticion no paso por el gateway: 401. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> alFaltarCabecera(MissingRequestHeaderException falta) {
        boolean esIdentidad = CABECERA_USUARIO.equals(falta.getHeaderName());
        HttpStatus estado = esIdentidad ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, esIdentidad
                ? "Inicia sesion para continuar."
                : "Falta la cabecera " + falta.getHeaderName() + ".");
        problema.setTitle(esIdentidad ? "Sesion requerida" : "Peticion incompleta");
        return ResponseEntity.status(estado).body(problema);
    }
}
