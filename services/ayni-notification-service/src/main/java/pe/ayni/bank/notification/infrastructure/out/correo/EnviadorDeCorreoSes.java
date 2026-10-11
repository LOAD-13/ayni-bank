package pe.ayni.bank.notification.infrastructure.out.correo;

import java.nio.charset.StandardCharsets;

import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.port.out.EnviadorDeCorreoPort;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Envio por Amazon SES. Las credenciales salen del rol de la instancia EC2, igual que en
 * identity (ADR-0026): no hay claves en ningun fichero.
 */
public class EnviadorDeCorreoSes implements EnviadorDeCorreoPort {

    private final SesV2Client ses;
    private final String remitente;

    public EnviadorDeCorreoSes(SesV2Client ses, String remitente) {
        this.ses = ses;
        this.remitente = remitente;
    }

    @Override
    public void enviar(Titular destinatario, MensajeRedactado mensaje) {
        ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(remitente)
                .destination(d -> d.toAddresses(destinatario.correo()))
                .content(c -> c.simple(m -> m
                        .subject(contenido(mensaje.asunto()))
                        .body(b -> b
                                .html(contenido(CorreoHtml.html(destinatario, mensaje)))
                                .text(contenido(CorreoHtml.texto(destinatario, mensaje))))))
                .build());
    }

    private static Content contenido(String datos) {
        return Content.builder().data(datos).charset(StandardCharsets.UTF_8.name()).build();
    }
}
