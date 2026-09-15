package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeVerificacionKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Implementa el limite de tres intentos y la derivacion a revision manual (AYNI-13
 * subtarea 11, ADR-0021), y el aviso al solicitante cuando eso ocurre (subtarea 14,
 * ADR-0024 — el criterio de aceptacion de AYNI-13 en Jira lo pide explicitamente en los
 * escenarios 4 y 5, y no tenia todavia ningun consumidor).
 */
@Service
public class GestionarFalloDeVerificacionKycService implements GestionarFalloDeVerificacionKycUseCase {

    private static final Logger log = LoggerFactory.getLogger(GestionarFalloDeVerificacionKycService.class);

    /** Sprint backlog, subtarea 11: "limite de tres intentos". */
    private static final int LIMITE_DE_INTENTOS = 3;

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeUsuariosPort usuarios;
    private final NotificadorDeVerificacionKycPort notificador;

    public GestionarFalloDeVerificacionKycService(RepositorioDeSolicitudesPort solicitudes,
                                                  RepositorioDeUsuariosPort usuarios,
                                                  NotificadorDeVerificacionKycPort notificador) {
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
        this.notificador = notificador;
    }

    @Override
    @Transactional
    public ResultadoDelIntentoKyc registrarFalloDeUsuario(UUID solicitudId) {
        int intentos = solicitudes.registrarIntentoFallidoDeKyc(solicitudId);

        if (intentos >= LIMITE_DE_INTENTOS) {
            derivarARevisionManual(solicitudId,
                    "Solicitud derivada a revision manual tras agotar los {} intentos. solicitudId={}",
                    LIMITE_DE_INTENTOS, solicitudId);
            return ResultadoDelIntentoKyc.DERIVADA_A_REVISION_MANUAL;
        }

        log.info("Intento de verificacion KYC fallido ({}/{}). solicitudId={}",
                intentos, LIMITE_DE_INTENTOS, solicitudId);
        return ResultadoDelIntentoKyc.PUEDE_REINTENTAR;
    }

    @Override
    @Transactional
    public void derivarPorServicioNoDisponible(UUID solicitudId) {
        derivarARevisionManual(solicitudId,
                "Solicitud derivada a revision manual: kyc-service no disponible. solicitudId={}",
                solicitudId);
    }

    /**
     * Marca la solicitud y avisa al titular. Sin titular (senuelo, o el usuario ya no
     * existe) no hay a quien avisar; eso no es un fallo de esta operacion, es el mismo caso
     * ya resuelto en {@code AprobarSolicitudService}.
     */
    private void derivarARevisionManual(UUID solicitudId, String plantillaDeLog, Object... argumentos) {
        solicitudes.marcarEnRevisionManual(solicitudId);
        log.info(plantillaDeLog, argumentos);

        solicitudes.titularDe(solicitudId)
                .flatMap(usuarios::buscarPorId)
                .map(Usuario::correo)
                .ifPresent(notificador::avisarEnRevisionManual);
    }
}
