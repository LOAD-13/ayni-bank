package pe.ayni.bank.notification.domain.port.out;

import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Titular;

/** Entrega un correo. Si no puede, lanza: el despachador decide si se reintenta. */
public interface EnviadorDeCorreoPort {

    void enviar(Titular destinatario, MensajeRedactado mensaje);
}
