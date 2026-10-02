package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.SolicitudNoAprobableException;
import pe.ayni.bank.identity.domain.port.in.AprobarSolicitudUseCase;
import pe.ayni.bank.identity.domain.port.out.PublicadorDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Aprueba una solicitud de onboarding y anuncia el hecho.
 *
 * <p>Es la bisagra del esqueleto ambulante: a partir de aqui el trabajo sigue en otro
 * servicio, que abrira la cuenta al oir el evento.
 *
 * <p><strong>Un senuelo no se puede aprobar.</strong> Las solicitudes que devuelve el
 * registro ante un correo ya existente no tienen usuario detras (ADR-0008), y aprobarlas
 * abriria una cuenta que no pertenece a nadie. Es el caso que primero hay que descartar.
 */
@Service
public class AprobarSolicitudService implements AprobarSolicitudUseCase {

    private static final Logger log = LoggerFactory.getLogger(AprobarSolicitudService.class);

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeUsuariosPort usuarios;
    private final PublicadorDeSolicitudesPort publicador;

    public AprobarSolicitudService(RepositorioDeSolicitudesPort solicitudes,
                                   RepositorioDeUsuariosPort usuarios,
                                   PublicadorDeSolicitudesPort publicador) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.publicador = publicador;
    }

    @Override
    @Transactional
    public void aprobar(UUID solicitudId) {
        UUID usuarioId = solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoAprobableException(
                        "La solicitud no existe o es un senuelo sin titular."));

        usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new SolicitudNoAprobableException(
                        "La solicitud apunta a un usuario que ya no existe."));

        solicitudes.marcarAprobada(solicitudId);
        usuarios.guardar(usuarios.buscarPorId(usuarioId).orElseThrow().activar());

        publicador.anunciarAprobacion(solicitudId, usuarioId);

        log.info("Solicitud aprobada. solicitudId={} usuarioId={}", solicitudId, usuarioId);
    }
}
