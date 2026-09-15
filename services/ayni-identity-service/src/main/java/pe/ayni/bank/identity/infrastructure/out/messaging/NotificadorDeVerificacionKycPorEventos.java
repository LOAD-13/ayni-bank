package pe.ayni.bank.identity.infrastructure.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeVerificacionKycPort;

/**
 * Implementacion provisional del aviso de revision manual, por el mismo motivo y con las
 * mismas cautelas que {@link NotificadorDeSeguridadPorEventos}: la entrega real la hara
 * {@code ayni-notification-service} a traves del outbox (ADR-0003), y eso llega con HU-13.
 */
@Component
public class NotificadorDeVerificacionKycPorEventos implements NotificadorDeVerificacionKycPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorDeVerificacionKycPorEventos.class);

    @Override
    public void avisarEnRevisionManual(CorreoElectronico correo) {
        if (log.isInfoEnabled()) {
            log.info("Pendiente de publicar por outbox: plantilla={} destinatario={}",
                    "VERIFICACION_EN_REVISION_MANUAL", correo.enmascarado());
        }
    }
}
