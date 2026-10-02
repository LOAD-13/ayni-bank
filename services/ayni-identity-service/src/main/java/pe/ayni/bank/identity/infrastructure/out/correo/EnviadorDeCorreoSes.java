package pe.ayni.bank.identity.infrastructure.out.correo;

import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Envio por Amazon SES.
 *
 * <p>Las credenciales no estan en ningun fichero: el SDK las toma del rol de la instancia
 * EC2, que solo tiene permiso de enviar desde el remitente configurado. Ver ADR-0026.
 */
public class EnviadorDeCorreoSes implements EnviadorDeCorreo {

    private final SesV2Client ses;
    private final String remitente;

    public EnviadorDeCorreoSes(SesV2Client ses, String remitente) {
        this.ses = ses;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String asunto, String html, String texto) {
        ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(remitente)
                .destination(Destination.builder().toAddresses(destinatario).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(contenido(asunto))
                        .body(Body.builder().html(contenido(html)).text(contenido(texto)).build())
                        .build()).build())
                .build());
    }

    private static Content contenido(String datos) {
        return Content.builder().data(datos).charset(StandardCharsets.UTF_8.name()).build();
    }
}
