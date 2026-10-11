package pe.ayni.bank.notification.infrastructure.out.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.Titular;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

class EnviadorDeCorreoSesTest {

    private static final Titular ANA = new Titular(UUID.randomUUID(), "ana@example.pe", "Ana <b>");
    private static final MensajeRedactado MENSAJE =
            new MensajeRedactado("Recibiste un depósito", "Recibiste PEN 500.00. Tu saldo es PEN 1,500.00 & más.");

    @Test
    @DisplayName("envia por SES desde el remitente, en UTF-8, con HTML escapado y version de texto")
    void envia() {
        SesV2Client ses = mock(SesV2Client.class);
        var peticion = ArgumentCaptor.forClass(SendEmailRequest.class);

        new EnviadorDeCorreoSes(ses, "Ayni Bank <no-responder@ayni.pe>").enviar(ANA, MENSAJE);

        verify(ses).sendEmail(peticion.capture());
        SendEmailRequest r = peticion.getValue();
        assertThat(r.fromEmailAddress()).isEqualTo("Ayni Bank <no-responder@ayni.pe>");
        assertThat(r.destination().toAddresses()).containsExactly("ana@example.pe");
        var simple = r.content().simple();
        assertThat(simple.subject().data()).isEqualTo("Recibiste un depósito");
        assertThat(simple.subject().charset()).isEqualTo("UTF-8");
        assertThat(simple.body().html().data())
                .contains("Hola Ana &lt;b&gt;,", "PEN 1,500.00 &amp; m&aacute;s.")
                .doesNotContain("<b>");
        assertThat(simple.body().text().data())
                .startsWith("Hola Ana <b>,\n\nRecibiste PEN 500.00.")
                .contains("Nunca te pediremos claves");
    }

    @Test
    @DisplayName("sin nombre de pila el saludo queda neutro")
    void saludoNeutro() {
        assertThat(CorreoHtml.saludo(new Titular(UUID.randomUUID(), "x@example.pe", ""))).isEqualTo("Hola,");
    }

    @Test
    @DisplayName("fuera de produccion el correo solo se anota")
    void bitacora() {
        assertThatCode(() -> new EnviadorDeCorreoEnBitacora().enviar(ANA, MENSAJE)).doesNotThrowAnyException();
    }
}
