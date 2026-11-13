package pe.ayni.bank.notification.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;
import pe.ayni.bank.notification.domain.model.Notificacion;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;

class AdaptadorRepositorioDeNotificacionesTest {

    @Test
    void si_los_datos_no_se_pueden_serializar_no_guarda_nada() {
        NotificacionJpaRepository notificaciones = mock(NotificacionJpaRepository.class);
        JsonMapper mapeador = mock(JsonMapper.class);
        when(mapeador.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
        var adaptador = new AdaptadorRepositorioDeNotificaciones(notificaciones,
                mock(IntentoEnvioJpaRepository.class), mapeador);
        Notificacion aviso = Notificacion.de(new AvisoDeMovimiento(UUID.randomUUID(), UUID.randomUUID(),
                TipoDeAviso.DEPOSITO_RECIBIDO, Map.of("importe", "10.00")), Instant.now());

        assertThatThrownBy(() -> adaptador.registrarSiEsNueva(aviso))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("serializar");
        verifyNoInteractions(notificaciones);
    }
}
