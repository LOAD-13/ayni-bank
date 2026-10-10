package pe.ayni.bank.identity.infrastructure.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRecuperacionPort;

/**
 * Implementacion provisional de los correos de HU-21 fuera de produccion, con las mismas
 * cautelas que {@link NotificadorDeSeguridadPorEventos}: solo deja rastro en el log, con el
 * destinatario enmascarado.
 *
 * <p><strong>El enlace no se escribe nunca</strong>, tampoco en desarrollo: un log con
 * enlaces de recuperacion es una lista de llaves de cuentas, y los logs se copian, se
 * comparten y se pegan en tickets.
 */
@Component
@Profile("!prod")
public class NotificadorDeRecuperacionPorEventos implements NotificadorDeRecuperacionPort {

    private static final Logger log = LoggerFactory.getLogger(NotificadorDeRecuperacionPorEventos.class);

    @Override
    public void enviarEnlaceDeRecuperacion(CorreoElectronico correo, String tokenEnClaro) {
        anotar("RECUPERACION", correo);
    }

    @Override
    public void avisarContrasenaCambiada(CorreoElectronico correo) {
        anotar("CONTRASENA_CAMBIADA", correo);
    }

    private void anotar(String plantilla, CorreoElectronico correo) {
        if (log.isInfoEnabled()) {
            log.info("Pendiente de publicar por outbox: plantilla={} destinatario={}",
                    plantilla, correo.enmascarado());
        }
    }
}
