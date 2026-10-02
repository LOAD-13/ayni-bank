package pe.ayni.bank.identity.infrastructure.out.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.infrastructure.config.ConfiguracionDeCorreo;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

/** Correo real de produccion: que plantilla sale, con que dato y que nunca rompe la operacion. */
class NotificadorPorCorreoTest {

    private record Enviado(String para, String asunto, String html, String texto) {
    }

    private final List<Enviado> enviados = new ArrayList<>();
    private final NotificadorPorCorreo notificador = new NotificadorPorCorreo(
            (para, asunto, html, texto) -> enviados.add(new Enviado(para, asunto, html, texto)),
            "https://ayni.example.pe/");
    private final CorreoElectronico ana = new CorreoElectronico("ana.quispe@example.pe");

    @Test
    @DisplayName("la bienvenida lleva el enlace para continuar el registro con su solicitud")
    void bienvenidaConEnlace() {
        UUID solicitud = UUID.randomUUID();
        notificador.enviarBienvenida(ana, solicitud);

        assertThat(enviados).singleElement().satisfies(e -> {
            assertThat(e.para()).isEqualTo("ana.quispe@example.pe");
            assertThat(e.html()).contains("https://ayni.example.pe/registro/dni-anverso?solicitudId=" + solicitud);
            assertThat(e.texto()).contains(solicitud.toString());
        });
    }

    @Test
    @DisplayName("el codigo por correo va destacado en el cuerpo, sin boton")
    void codigoDeVerificacion() {
        notificador.enviarCodigoPorCorreo(ana, "482915");
        assertThat(enviados.get(0).asunto()).contains("codigo");
        assertThat(enviados.get(0).html()).contains("482915").doesNotContain("<a href");
    }

    @Test
    @DisplayName("los avisos de seguridad, de intento de registro y de revision llevan su plantilla")
    void avisos() {
        notificador.avisarIntentoDeRegistroSobreCuentaExistente(ana);
        notificador.avisarIngresoPausado(ana);
        notificador.avisarSesionCerradaPorSeguridad(ana);
        notificador.avisarEnRevisionManual(ana);

        assertThat(enviados).extracting(Enviado::asunto).containsExactly(
                PlantillaDeCorreo.INTENTO_DE_REGISTRO.asunto(),
                PlantillaDeCorreo.INGRESO_PAUSADO.asunto(),
                PlantillaDeCorreo.SESION_CERRADA.asunto(),
                PlantillaDeCorreo.REVISION_MANUAL.asunto());
    }

    @Test
    @DisplayName("el SMS no tiene canal: no envia nada ni falla")
    void smsSinCanal() {
        notificador.enviarCodigoPorSms(new Celular("987654321"), "123456");
        assertThat(enviados).isEmpty();
    }

    @Test
    @DisplayName("si SES falla, la operacion que origino el correo no se rompe")
    void unFalloDeEnvioNoPropaga() {
        NotificadorPorCorreo conFallo = new NotificadorPorCorreo(
                (p, a, h, t) -> { throw new IllegalStateException("SES caido"); }, "https://x");
        conFallo.enviarBienvenida(ana, UUID.randomUUID());
        assertThat(enviados).isEmpty();
    }

    @Test
    @DisplayName("el dato se escapa: un enlace con HTML no inyecta marcado")
    void escapaElDato() {
        String html = PlantillaDeCorreo.CODIGO_DE_VERIFICACION.html("<script>alert(1)</script>");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("SES recibe remitente, destinatario, asunto y las dos versiones del cuerpo")
    void enviadorSes() {
        SesV2Client ses = mock(SesV2Client.class);
        when(ses.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().build());
        var captor = ArgumentCaptor.forClass(SendEmailRequest.class);

        new EnviadorDeCorreoSes(ses, "Ayni Bank <no-responder@ayni.pe>")
                .enviar("ana@example.pe", "Asunto", "<p>hola</p>", "hola");

        verify(ses).sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();
        assertThat(req.fromEmailAddress()).isEqualTo("Ayni Bank <no-responder@ayni.pe>");
        assertThat(req.destination().toAddresses()).containsExactly("ana@example.pe");
        assertThat(req.content().simple().body().html().data()).isEqualTo("<p>hola</p>");
        assertThat(req.content().simple().body().text().data()).isEqualTo("hola");
    }

    @Test
    @DisplayName("la configuracion arma el cliente de SES y el enviador")
    void configuracion() {
        ConfiguracionDeCorreo configuracion = new ConfiguracionDeCorreo();
        try (SesV2Client cliente = configuracion.sesV2Client("us-east-1")) {
            assertThat(configuracion.enviadorDeCorreo(cliente, "a@b.pe")).isInstanceOf(EnviadorDeCorreoSes.class);
        }
    }
}
