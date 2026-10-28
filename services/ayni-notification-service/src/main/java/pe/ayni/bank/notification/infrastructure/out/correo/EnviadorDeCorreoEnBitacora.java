package pe.ayni.bank.notification.infrastructure.out.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.out.EnviadorDeCorreoPort;

/**
 * Fuera de produccion no sale ningun correo: se anota en el log que se habria enviado.
 * Ni la direccion ni el cuerpo aparecen en la traza, solo el titular y el asunto.
 */
public class EnviadorDeCorreoEnBitacora implements EnviadorDeCorreoPort {

    private static final Logger log = LoggerFactory.getLogger(EnviadorDeCorreoEnBitacora.class);

    @Override
    public void enviar(Titular destinatario, MensajeRedactado mensaje) {
        log.info("Correo simulado para el titular {}: «{}»", destinatario.usuarioId(), mensaje.asunto());
    }
}
