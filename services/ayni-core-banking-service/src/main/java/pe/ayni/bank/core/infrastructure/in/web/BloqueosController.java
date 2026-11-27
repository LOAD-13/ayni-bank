package pe.ayni.bank.core.infrastructure.in.web;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.domain.model.CuatroOjosException;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException;
import pe.ayni.bank.core.domain.port.in.BloquearCuentaUseCase;

/**
 * Back-office: bloqueo de la cuenta de un cliente con cuatro ojos (HU-15, AYNI-282). El operador
 * lo pide ({@value #PERMISO_INICIAR}) y un supervisor lo aprueba ({@value #PERMISO_APROBAR}).
 *
 * <p>El gateway ya exige el permiso de cada ruta; aqui se vuelve a comprobar con la cabecera que
 * el gateway pone y borra de lo que manda el cliente, como en la pista de auditoria. Que quien
 * aprueba no sea quien inicio lo hace cumplir el caso de uso, y la base de datos detras.
 */
@RestController
@RequestMapping("/api/v1/backoffice/bloqueos")
public class BloqueosController {

    static final String PERMISO_INICIAR = "cliente:bloquear_iniciar";
    static final String PERMISO_APROBAR = "cliente:bloquear_aprobar";
    private static final Logger log = LoggerFactory.getLogger(BloqueosController.class);

    private final BloquearCuentaUseCase bloqueos;

    public BloqueosController(BloquearCuentaUseCase bloqueos) {
        this.bloqueos = bloqueos;
    }

    @PostMapping
    public ResponseEntity<SolicitudDto> iniciar(
            @RequestHeader(OperacionesController.CABECERA_USUARIO) UUID operador,
            @RequestHeader(value = AuditoriaController.CABECERA_PERMISOS, required = false) String permisos,
            @RequestBody PeticionDeBloqueo peticion) {
        exigir(permisos, PERMISO_INICIAR, operador);
        SolicitudDeBloqueo solicitud = bloqueos.iniciar(operador, peticion.numeroDeCuenta(), peticion.motivo());
        return ResponseEntity.status(HttpStatus.CREATED).body(SolicitudDto.desde(solicitud));
    }

    @GetMapping
    public List<SolicitudDto> pendientes(
            @RequestHeader(OperacionesController.CABECERA_USUARIO) UUID supervisor,
            @RequestHeader(value = AuditoriaController.CABECERA_PERMISOS, required = false) String permisos) {
        exigir(permisos, PERMISO_APROBAR, supervisor);
        return bloqueos.pendientes().stream().map(SolicitudDto::desde).toList();
    }

    @PostMapping("/{id}/aprobacion")
    public SolicitudDto aprobar(
            @RequestHeader(OperacionesController.CABECERA_USUARIO) UUID supervisor,
            @RequestHeader(value = AuditoriaController.CABECERA_PERMISOS, required = false) String permisos,
            @PathVariable UUID id) {
        exigir(permisos, PERMISO_APROBAR, supervisor);
        return SolicitudDto.desde(bloqueos.aprobar(supervisor, id));
    }

    private static void exigir(String permisos, String permiso, UUID usuario) {
        if (permisos == null || !Arrays.asList(permisos.split(",")).contains(permiso)) {
            log.warn("Bloqueo de cuenta denegado: sin el permiso {}. usuarioId={}", permiso, usuario);
            throw new SinPermisoException();
        }
    }

    @ExceptionHandler(SinPermisoException.class)
    public ResponseEntity<ProblemDetail> alFaltarElPermiso() {
        return problema(HttpStatus.FORBIDDEN, "Permiso insuficiente", "PERMISO_INSUFICIENTE",
                "Tu rol no permite esta operacion.");
    }

    @ExceptionHandler(CuatroOjosException.class)
    public ResponseEntity<ProblemDetail> alAprobarLoPropio(CuatroOjosException e) {
        return problema(HttpStatus.FORBIDDEN, "Cuatro ojos", "CUATRO_OJOS", e.getMessage());
    }

    @ExceptionHandler(SolicitudDeBloqueoNoValidaException.class)
    public ResponseEntity<ProblemDetail> alNoPoder(SolicitudDeBloqueoNoValidaException e) {
        return problema(HttpStatus.CONFLICT, "Bloqueo no permitido", "BLOQUEO_NO_PERMITIDO", e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> alNoExistir(NoSuchElementException e) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", "NO_ENCONTRADO", e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String codigo,
                                                          String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return ResponseEntity.status(estado).body(problema);
    }

    private static final class SinPermisoException extends RuntimeException {
    }

    public record PeticionDeBloqueo(String numeroDeCuenta, String motivo) {
    }

    public record SolicitudDto(UUID id, UUID cuentaId, String motivo, UUID iniciadaPor, Instant iniciadaEn,
                               UUID aprobadaPor, Instant aprobadaEn, String estado) {

        static SolicitudDto desde(SolicitudDeBloqueo s) {
            return new SolicitudDto(s.id(), s.cuentaId(), s.motivo(), s.iniciadaPor(), s.iniciadaEn(),
                    s.aprobadaPor(), s.aprobadaEn(), s.pendiente() ? "PENDIENTE" : "APROBADA");
        }
    }
}
