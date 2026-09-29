package pe.ayni.bank.identity.infrastructure.out.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/** HU-22 (AYNI-124): notifica por outbox el despacho del segundo factor. */
class NotificadorDeSegundoFactorPorEventosTest {

    private final NotificadorDeSegundoFactorPorEventos notificador =
            new NotificadorDeSegundoFactorPorEventos();

    @Test
    void enviaPorCorreoSinFallar() {
        assertThatCode(() ->
                notificador.enviarCodigoPorCorreo(new CorreoElectronico("ana.quispe@example.pe"), "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void enviaPorSmsSinFallar() {
        assertThatCode(() ->
                notificador.enviarCodigoPorSms(new Celular("987654321"), "123456"))
                .doesNotThrowAnyException();
    }
}
