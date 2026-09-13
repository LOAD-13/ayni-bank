package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * Implementa el limite de tres intentos y la derivacion a revision manual (AYNI-13
 * subtarea 11, ADR-0021).
 */
@Service
public class GestionarFalloDeVerificacionKycService implements GestionarFalloDeVerificacionKycUseCase {

    private static final Logger log = LoggerFactory.getLogger(GestionarFalloDeVerificacionKycService.class);

    /** Sprint backlog, subtarea 11: "limite de tres intentos". */
    private static final int LIMITE_DE_INTENTOS = 3;

    private final RepositorioDeSolicitudesPort solicitudes;

    public GestionarFalloDeVerificacionKycService(RepositorioDeSolicitudesPort solicitudes) {
        this.solicitudes = solicitudes;
    }

    @Override
    @Transactional
    public ResultadoDelIntentoKyc registrarFalloDeUsuario(UUID solicitudId) {
        int intentos = solicitudes.registrarIntentoFallidoDeKyc(solicitudId);

        if (intentos >= LIMITE_DE_INTENTOS) {
            solicitudes.marcarEnRevisionManual(solicitudId);
            log.info("Solicitud derivada a revision manual tras agotar los {} intentos. "
                    + "solicitudId={}", LIMITE_DE_INTENTOS, solicitudId);
            return ResultadoDelIntentoKyc.DERIVADA_A_REVISION_MANUAL;
        }

        log.info("Intento de verificacion KYC fallido ({}/{}). solicitudId={}",
                intentos, LIMITE_DE_INTENTOS, solicitudId);
        return ResultadoDelIntentoKyc.PUEDE_REINTENTAR;
    }

    @Override
    @Transactional
    public void derivarPorServicioNoDisponible(UUID solicitudId) {
        solicitudes.marcarEnRevisionManual(solicitudId);
        log.warn("Solicitud derivada a revision manual: kyc-service no disponible. "
                + "solicitudId={}", solicitudId);
    }
}
