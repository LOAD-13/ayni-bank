package pe.ayni.bank.identity.infrastructure.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.port.out.NotificadorDeSegundoFactorPort;

/**
 * Envio real de OTP por SMTP, exclusivo del perfil {@code e2e}.
 *
 * <p>No sustituye a {@code ayni-notification-service} ni al outbox transaccional de
 * ADR-0003 (eso sigue siendo HU-13): existe solo para poder verificar, en una prueba
 * de extremo a extremo, que un codigo generado llega de verdad a una bandeja, algo que
 * {@link NotificadorDeSegundoFactorPorEventos} —el bean de siempre, que solo deja
 * rastro en el log— no puede demostrar por si mismo.
 *
 * <p>El remitente es una cuenta de prueba de Ethereal (ethereal.email), no una cuenta
 * real: Ethereal no reenvia el correo a ningun destinatario final, asi que activar
 * este perfil nunca puede filtrar un codigo a una bandeja ajena por accidente. El
 * mensaje solo queda visible para quien tenga las credenciales de esa cuenta de
 * prueba, en ethereal.email.
 *
 * <p>SMS no tiene equivalente de prueba real aqui: Ethereal es solo SMTP. Se deja el
 * mismo log provisional que el resto de la aplicacion hasta que exista un canal.
 */
@Component
@Profile("e2e")
public class NotificadorDeSegundoFactorPorCorreoDePrueba implements NotificadorDeSegundoFactorPort {

    private static final Logger log =
            LoggerFactory.getLogger(NotificadorDeSegundoFactorPorCorreoDePrueba.class);

    private final JavaMailSender remitente;

    public NotificadorDeSegundoFactorPorCorreoDePrueba(JavaMailSender remitente) {
        this.remitente = remitente;
    }

    @Override
    public void enviarCodigoPorCorreo(CorreoElectronico correo, String codigo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(correo.valor());
        mensaje.setSubject("Tu código de verificación Ayni Bank");
        mensaje.setText("""
                Tu código de verificación es: %s

                Vence en 10 minutos. Nadie de Ayni Bank te lo pedirá por teléfono, \
                correo ni mensaje. Si te lo piden, no lo compartas.""".formatted(codigo));

        remitente.send(mensaje);
        log.info("Codigo de verificacion enviado por SMTP de prueba (perfil e2e). destinatario={}",
                correo.enmascarado());
    }

    @Override
    public void enviarCodigoPorSms(Celular celular, String codigo) {
        log.info("Perfil e2e sin canal SMTP de prueba para SMS. "
                + "Pendiente de publicar por outbox: plantilla=CODIGO_VERIFICACION destinatario={}",
                celular.enmascarado());
    }
}
