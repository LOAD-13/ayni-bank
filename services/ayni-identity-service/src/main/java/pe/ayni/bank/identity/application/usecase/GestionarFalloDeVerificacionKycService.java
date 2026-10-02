package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeVerificacionKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeUsuariosPort;

/**
 * Implementa el limite de tres intentos por lado del DNI y la derivacion a revision manual
 * (AYNI-13, ADR-0021 y ADR-0026), y el aviso al solicitante cuando eso ocurre (ADR-0024).
 *
 * <p>Una solicitud que ya esta en revision manual no suma mas intentos ni vuelve a avisar:
 * un cuarto intento sobre ella se responde como derivada (escenario 4 de Jira), sin que el
 * contador pase de tres ni el titular reciba el mismo correo dos veces.
 */
@Service
public class GestionarFalloDeVerificacionKycService implements GestionarFalloDeVerificacionKycUseCase {

    private static final Logger log = LoggerFactory.getLogger(GestionarFalloDeVerificacionKycService.class);

    /** Criterio de aceptacion de HU-02: "maximo 3 intentos de captura por lado". */
    static final int LIMITE_DE_INTENTOS_POR_LADO = 3;

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
    public ResultadoDelIntentoKyc registrarFalloDeUsuario(UUID solicitudId, TipoDeDocumentoKyc lado) {
        if (solicitudes.estaEnRevisionManual(solicitudId)) {
            return ResultadoDelIntentoKyc.derivada();
        }

        int intentos = solicitudes.registrarIntentoFallidoDeKyc(solicitudId, lado);

        if (intentos >= LIMITE_DE_INTENTOS_POR_LADO) {
            derivarARevisionManual(solicitudId,
                    "Solicitud derivada a revision manual tras agotar los {} intentos del {}. solicitudId={}",
                    LIMITE_DE_INTENTOS_POR_LADO, lado, solicitudId);
            return ResultadoDelIntentoKyc.derivada();
        }

        log.info("Intento de verificacion KYC fallido en el {} ({}/{}). solicitudId={}",
                lado, intentos, LIMITE_DE_INTENTOS_POR_LADO, solicitudId);
        return ResultadoDelIntentoKyc.puedeReintentar(LIMITE_DE_INTENTOS_POR_LADO - intentos);
    }

    @Override
    @Transactional
    public void derivarPorServicioNoDisponible(UUID solicitudId) {
        derivarARevisionManual(solicitudId,
                "Solicitud derivada a revision manual: kyc-service no disponible. solicitudId={}",
                solicitudId);
    }

    @Override
    @Transactional
    public void derivarPorDiscrepancia(UUID solicitudId) {
        derivarARevisionManual(solicitudId,
                "Solicitud derivada a revision manual: el DNI no cuadra con lo declarado. solicitudId={}",
                solicitudId);
    }

    /**
     * Marca la solicitud y avisa al titular, una sola vez. Sin titular (senuelo, o el usuario
     * ya no existe) no hay a quien avisar; eso no es un fallo de esta operacion, es el mismo
     * caso ya resuelto en {@code AprobarSolicitudService}.
     */
    private void derivarARevisionManual(UUID solicitudId, String plantillaDeLog, Object... argumentos) {
        if (solicitudes.estaEnRevisionManual(solicitudId)) {
            return;
        }
        solicitudes.marcarEnRevisionManual(solicitudId);
        log.info(plantillaDeLog, argumentos);

        solicitudes.titularDe(solicitudId)
                .flatMap(usuarios::buscarPorId)
                .map(Usuario::correo)
                .ifPresent(notificador::avisarEnRevisionManual);
    }
}
