package pe.ayni.bank.identity.application.usecase;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeSolicitud;
import pe.ayni.bank.identity.domain.model.SolicitudNoRechazableException;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.RechazarSolicitudUseCase;
import pe.ayni.bank.identity.domain.port.out.PublicadorDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Rechaza una solicitud de onboarding y avisa al solicitante del motivo. HU-05, escenario 3.
 *
 * <p>Es la contraparte de {@link AprobarSolicitudService} y tiene sus mismas barreras: un
 * senuelo o una solicitud inexistente no se tocan (ADR-0008). Y una mas: una solicitud ya
 * cerrada no cambia de resultado, porque una aprobada ya abrio una cuenta.
 *
 * <p>El usuario no se activa ni se bloquea. Bloquearlo cerraria la puerta para siempre por
 * algo que puede ser una foto mal tomada; volver a intentarlo queda fuera de esta historia.
 */
@Service
public class RechazarSolicitudService implements RechazarSolicitudUseCase {

    private static final Logger log = LoggerFactory.getLogger(RechazarSolicitudService.class);

    /** Estados en los que la solicitud ya tiene un resultado que no se puede cambiar. */
    private static final Set<String> CERRADAS = Set.of("APROBADA", "RECHAZADA", "EXPIRADA");

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeUsuariosPort usuarios;
    private final PublicadorDeSolicitudesPort publicador;

    public RechazarSolicitudService(RepositorioDeSolicitudesPort solicitudes,
                                    RepositorioDeUsuariosPort usuarios,
                                    PublicadorDeSolicitudesPort publicador) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.publicador = publicador;
    }

    @Override
    @Transactional
    public void rechazar(UUID solicitudId, MotivoDeRechazoDeSolicitud motivo) {
        UUID usuarioId = solicitudes.titularDe(solicitudId)
                .orElseThrow(SolicitudNoRechazableException::inexistente);
        Usuario usuario = usuarios.buscarPorId(usuarioId)
                .orElseThrow(SolicitudNoRechazableException::inexistente);
        String estado = solicitudes.estadoDe(solicitudId)
                .orElseThrow(SolicitudNoRechazableException::inexistente);
        if (CERRADAS.contains(estado)) {
            throw SolicitudNoRechazableException.cerrada(estado);
        }

        solicitudes.marcarRechazada(solicitudId, motivo);
        publicador.anunciarRechazo(solicitudId, usuarioId, usuario.correo(),
                solicitudes.nombreDePilaDe(usuarioId).orElse(""), motivo);

        // Sin correo ni nombre en el log: es dato personal y el identificador basta.
        log.info("Solicitud rechazada. solicitudId={} usuarioId={} motivo={}", solicitudId, usuarioId, motivo);
    }
}
