package pe.ayni.bank.identity.infrastructure.out.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/** HU-22 (AYNI-124): envio real de OTP por SMTP de prueba, exclusivo del perfil e2e. */
class NotificadorDeSegundoFactorPorCorreoDePruebaTest {

    private final JavaMailSender remitente = mock(JavaMailSender.class);
    private final NotificadorDeSegundoFactorPorCorreoDePrueba notificador =
            new NotificadorDeSegundoFactorPorCorreoDePrueba(remitente);

    @Test
    void enviaElCorreoConElCodigo() {
        notificador.enviarCodigoPorCorreo(new CorreoElectronico("ana.quispe@example.pe"), "123456");

        verify(remitente).send(any(SimpleMailMessage.class));
    }

    @Test
    void enviarPorSmsNoFallaAunqueNoHayaCanalDePrueba() {
        assertThatCode(() ->
                notificador.enviarCodigoPorSms(new Celular("987654321"), "123456"))
                .doesNotThrowAnyException();
    }
}
