package pe.ayni.bank.core.infrastructure.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.domain.model.EventoDeAuditoria;
import pe.ayni.bank.core.domain.model.HistorialDeCuenta;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;
import pe.ayni.bank.core.domain.port.in.ConsultarAuditoriaUseCase;
import pe.ayni.bank.core.domain.model.VersionDeCuenta;
import pe.ayni.bank.core.domain.port.in.ConsultarHistorialDeCuentaUseCase;

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
    private final ConsultarHistorialDeCuentaUseCase historial;

    public AuditoriaController(ConsultarAuditoriaUseCase consultar, ConsultarHistorialDeCuentaUseCase historial) {
        this.consultar = consultar;
        this.historial = historial;
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
        exigirPermiso(permisos, auditor);
        PaginaDeAuditoria resultado = consultar.consultar(usuarioId, tipo, desde, hasta,
                Math.max(0, pagina), Math.clamp(tamano, 1, MAXIMO_POR_PAGINA));
        log.info("Pista de auditoria leida. auditorId={} filas={}", auditor, resultado.eventos().size());
        return PaginaDto.desde(resultado);
    }

    /**
     * HU-15, escenario 2 (AYNI-280): como estaba la cuenta en una fecha pasada y quien hizo cada
     * cambio. Sin {@code en}, la version de ahora.
     */
    @GetMapping("/api/v1/auditoria/cuentas/{numero}/historial")
    public HistorialDto historial(
            @RequestHeader(OperacionesController.CABECERA_USUARIO) UUID auditor,
            @RequestHeader(value = CABECERA_PERMISOS, required = false) String permisos,
            @PathVariable String numero,
            @RequestParam(required = false) Instant en) {
        exigirPermiso(permisos, auditor);
        HistorialDeCuenta resultado = historial.consultar(numero, en == null ? Instant.now() : en);
        log.info("Historial de cuenta leido. auditorId={} versiones={}", auditor, resultado.versiones().size());
        return HistorialDto.desde(resultado);
    }

    private static void exigirPermiso(String permisos, UUID auditor) {
        if (permisos == null || !Arrays.asList(permisos.split(",")).contains(PERMISO)) {
            log.warn("Lectura de la pista denegada: sin el permiso {}. usuarioId={}", PERMISO, auditor);
            throw new PermisoInsuficienteException();
        }
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> alNoExistir(NoSuchElementException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Cuenta no encontrada");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problema);
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

    public record VersionDto(long revision, String momento, String actor, String tipo, String numero, String estado,
                             String moneda) {

        static VersionDto desde(VersionDeCuenta v) {
            return new VersionDto(v.revision(), v.momento().toString(), v.actor() == null ? null : v.actor().toString(),
                    v.tipo().name(), v.numero(), v.estado() == null ? null : v.estado().name(),
                    v.moneda() == null ? null : v.moneda().name());
        }
    }

    /** {@code vigente} es nulo si la fecha es anterior a la primera version guardada. */
    public record HistorialDto(String fecha, VersionDto vigente, List<VersionDto> versiones) {

        static HistorialDto desde(HistorialDeCuenta h) {
            return new HistorialDto(h.fecha().toString(), h.vigente().map(VersionDto::desde).orElse(null),
                    h.versiones().stream().map(VersionDto::desde).toList());
        }
    }
}
