package pe.ayni.bank.identity.infrastructure.out.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/** AYNI-13 subtarea 14: aviso de revision manual. Ver ADR-0024. */
class NotificadorDeVerificacionKycPorEventosTest {

    @Test
    void avisaSinFallar() {
        var notificador = new NotificadorDeVerificacionKycPorEventos();

        assertThatCode(() ->
                notificador.avisarEnRevisionManual(new CorreoElectronico("ana.quispe@example.pe")))
                .doesNotThrowAnyException();
    }
}
