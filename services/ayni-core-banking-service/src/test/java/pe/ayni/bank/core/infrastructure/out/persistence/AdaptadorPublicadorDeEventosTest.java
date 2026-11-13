package pe.ayni.bank.core.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class AdaptadorPublicadorDeEventosTest {

    @Test
    void un_evento_que_no_se_puede_serializar_hace_fallar_la_transaccion() {
        OutboxJpaRepository outbox = mock(OutboxJpaRepository.class);
        JsonMapper mapeador = mock(JsonMapper.class);
        when(mapeador.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
        var adaptador = new AdaptadorPublicadorDeEventos(outbox, mapeador, Clock.systemUTC());

        assertThatThrownBy(() -> adaptador.registrar("Cuenta", UUID.randomUUID(), "CuentaAbierta", new Object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CuentaAbierta");
        verifyNoInteractions(outbox);
    }
}
