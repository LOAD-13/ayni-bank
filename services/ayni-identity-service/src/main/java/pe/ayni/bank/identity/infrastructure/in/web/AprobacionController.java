package pe.ayni.bank.identity.infrastructure.in.web;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.identity.domain.model.SolicitudNoAprobableException;
import pe.ayni.bank.identity.domain.port.in.AprobarSolicitudUseCase;

/**
 * Aprueba una solicitud de onboarding a mano.
 *
 * <p><strong>{@code @Profile("dev")}, y esa anotacion es lo mas importante de esta
 * clase.</strong> En Sprint 1 no hay OCR, asi que nada puede aprobar una solicitud, y sin
 * aprobacion no se puede demostrar que la cadena completa —registro, evento, apertura de
 * cuenta— funciona. Este endpoint ocupa ese hueco durante un sprint.
 *
 * <p>Publicado en staging o produccion seria un agujero de los grandes: cualquiera podria
 * saltarse la verificacion de identidad entera. El perfil garantiza que el bean no llegue
 * siquiera a crearse fuera de desarrollo. En Sprint 2 lo sustituye el resultado del KYC y
 * esta clase se borra.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/v1/dev/solicitudes")
public class AprobacionController {

    private final AprobarSolicitudUseCase aprobarSolicitud;

    public AprobacionController(AprobarSolicitudUseCase aprobarSolicitud) {
        this.aprobarSolicitud = aprobarSolicitud;
    }

    @PostMapping("/{solicitudId}/aprobar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void aprobar(@PathVariable UUID solicitudId) {
        aprobarSolicitud.aprobar(solicitudId);
    }

    @ExceptionHandler(SolicitudNoAprobableException.class)
    public ProblemDetail alNoPoderAprobar(
            SolicitudNoAprobableException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("La solicitud no se puede aprobar");
        problema.setDetail(excepcion.getMessage());
        return problema;
    }
}
