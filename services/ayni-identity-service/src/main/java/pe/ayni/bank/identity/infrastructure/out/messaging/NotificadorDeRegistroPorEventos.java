package pe.ayni.bank.identity.infrastructure.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeRegistroPort;

/**
 * Implementacion provisional del notificador: registra la intencion en el log.
 *
 * <p>La entrega real la hace {@code ayni-notification-service}, que ya tiene su catalogo
 * de plantillas, y el evento debe viajar por el patron Transactional Outbox (ADR-0003):
 * publicar en RabbitMQ desde dentro de la transaccion permitiria notificar un registro que
 * despues hace rollback. Eso llega con HU-13.
 *
 * <p>Se deja explicito que es provisional, y no oculto tras un nombre que aparente hacer
 * mas de lo que hace. Lo que si respeta ya es lo que no puede esperar: el correo se
 * registra enmascarado, porque es dato personal segun la Ley N.o 29733 y estos logs los
 * lee cualquiera con acceso a Loki.
 */
@Component
public class NotificadorDeRegistroPorEventos implements NotificadorDeRegistroPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorDeRegistroPorEventos.class);

    @Override
    public void enviarBienvenida(CorreoElectronico correo) {
        log.info("Pendiente de publicar por outbox: plantilla=BIENVENIDA destinatario={}",
                correo.enmascarado());
    }

    @Override
    public void avisarIntentoDeRegistroSobreCuentaExistente(CorreoElectronico correo) {
        log.info("Pendiente de publicar por outbox: plantilla=INTENTO_DE_REGISTRO "
                + "destinatario={}", correo.enmascarado());
    }
}
