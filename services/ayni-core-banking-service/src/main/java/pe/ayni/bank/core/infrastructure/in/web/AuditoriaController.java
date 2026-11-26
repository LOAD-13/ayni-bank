package pe.ayni.bank.core.infrastructure.in.web;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.domain.model.EventoDeAuditoria;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;
import pe.ayni.bank.core.domain.port.in.ConsultarAuditoriaUseCase;

/**
 * Lectura de la pista de auditoria para el back-office (HU-15, AYNI-284).
 *
 * <p>Solo {@code GET}: la pista es de solo insercion (V14) y aqui no hay ninguna ruta que escriba.
 * El gateway ya exige el permiso {@value #PERMISO} antes de que la peticion llegue; aqui se vuelve a
 * comprobar con la cabecera que el gateway pone y borra de lo que manda el cliente, porque leer
 * lo que hizo cada cliente es de lo mas sensible que hay y no debe depender de una sola barrera.
 */
@RestController
public class AuditoriaController {

    static final String PERMISO = "auditoria:leer";
    static final String CABECERA_PERMISOS = "X-Ayni-Permisos";
    private static final int MAXIMO_POR_PAGINA = 100;
    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private final ConsultarAuditoriaUseCase consultar;

    public AuditoriaController(ConsultarAuditoriaUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/api/v1/auditoria/eventos")
    public PaginaDto eventos(
            @RequestHeader(OperacionesController.CABECERA_USUARIO) UUID auditor,
            @RequestHeader(value = CABECERA_PERMISOS, required = false) String permisos,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z_]{1,40}$") String tipo,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        if (permisos == null || !Arrays.asList(permisos.split(",")).contains(PERMISO)) {
            log.warn("Lectura de la pista denegada: sin el permiso {}. usuarioId={}", PERMISO, auditor);
            throw new PermisoInsuficienteException();
        }
        PaginaDeAuditoria resultado = consultar.consultar(usuarioId, tipo, desde, hasta,
                Math.max(0, pagina), Math.clamp(tamano, 1, MAXIMO_POR_PAGINA));
        log.info("Pista de auditoria leida. auditorId={} filas={}", auditor, resultado.eventos().size());
        return PaginaDto.desde(resultado);
    }

    /** El gateway ya exige el permiso; esto es la segunda barrera. */
    @ExceptionHandler(PermisoInsuficienteException.class)
    public ResponseEntity<ProblemDetail> alFaltarElPermiso() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Tu rol no permite leer la pista de auditoria.");
        problema.setTitle("Permiso insuficiente");
        problema.setProperty("codigo", "PERMISO_INSUFICIENTE");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema);
    }

    private static final class PermisoInsuficienteException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> alPedirMal(IllegalArgumentException mal) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, mal.getMessage());
        problema.setTitle("Peticion no valida");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    /** Sin la cabecera de usuario la peticion no paso por el gateway: 401. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> alFaltarCabecera(MissingRequestHeaderException falta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Inicia sesion para continuar.");
        problema.setTitle("Sesion requerida");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problema);
    }

    // ── Contrato ──────────────────────────────────────────────────────────

    public record EventoDto(long id, String tipo, String usuarioId, String movimientoId, String motivo,
                            String resultado, String recurso, String ip, String agente, String ocurridoEn) {

        static EventoDto desde(EventoDeAuditoria e) {
            return new EventoDto(e.id(), e.tipo(), e.usuarioId().toString(),
                    e.movimientoId() == null ? null : e.movimientoId().toString(), e.motivo(), e.resultado(),
                    e.recurso(), e.ip(), e.agente(), e.ocurridoEn().toString());
        }
    }

    public record PaginaDto(List<EventoDto> eventos, int pagina, int tamano, long total) {

        static PaginaDto desde(PaginaDeAuditoria p) {
            return new PaginaDto(p.eventos().stream().map(EventoDto::desde).toList(), p.pagina(), p.tamano(),
                    p.total());
        }
    }
}
