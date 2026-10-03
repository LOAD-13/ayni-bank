package pe.ayni.bank.identity.infrastructure.in.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import pe.ayni.bank.identity.domain.model.SolicitudNoAprobableException;
import pe.ayni.bank.identity.domain.port.in.AprobarSolicitudUseCase;

/**
 * Aprobacion de una solicitud por un operador, tras revisar el caso a mano (cotejo facial
 * de la selfie, discrepancias o servicio no disponible).
 *
 * <p>Dos barreras. La primera: el gateway no enruta {@code /api/v1/operador/**}, asi que
 * desde internet no se llega; el operador lo invoca dentro de la red de los contenedores.
 * La segunda: exige la clave de operador ({@code AYNI_OPERADOR_CLAVE}, guardada en SSM) y,
 * si no esta configurada, el endpoint responde como si no existiera.
 */
@RestController
@RequestMapping("/api/v1/operador/solicitudes")
public class OperadorController {

    private static final Logger log = LoggerFactory.getLogger(OperadorController.class);

    private final AprobarSolicitudUseCase aprobarSolicitud;
    private final String claveDeOperador;

    public OperadorController(AprobarSolicitudUseCase aprobarSolicitud,
                              @Value("${ayni.operador.clave:}") String claveDeOperador) {
        this.aprobarSolicitud = aprobarSolicitud;
        this.claveDeOperador = claveDeOperador;
    }

    @PostMapping("/{solicitudId}/aprobar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void aprobar(@PathVariable UUID solicitudId,
                        @RequestHeader(name = "X-Operador-Clave", required = false) String clave) {
        if (!claveValida(clave)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        aprobarSolicitud.aprobar(solicitudId);
        log.info("Solicitud aprobada por un operador. solicitudId={}", solicitudId);
    }

    private boolean claveValida(String clave) {
        if (claveDeOperador.isBlank() || clave == null) {
            return false;
        }
        return MessageDigest.isEqual(claveDeOperador.getBytes(StandardCharsets.UTF_8),
                clave.getBytes(StandardCharsets.UTF_8));
    }

    @ExceptionHandler(SolicitudNoAprobableException.class)
    public ProblemDetail alNoPoderAprobar(SolicitudNoAprobableException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("La solicitud no se puede aprobar");
        problema.setDetail(excepcion.getMessage());
        return problema;
    }
}
