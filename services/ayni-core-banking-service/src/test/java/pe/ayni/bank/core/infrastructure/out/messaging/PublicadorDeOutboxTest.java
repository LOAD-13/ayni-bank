package pe.ayni.bank.core.infrastructure.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.core.infrastructure.out.persistence.BandejaDeSalida;
import pe.ayni.bank.core.infrastructure.out.persistence.BandejaDeSalida.EventoPendiente;

/** El outbox sale a RabbitMQ como JSON crudo y con el eventId que necesita HU-13. */
class PublicadorDeOutboxTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    private static final String CARGA = "{\"movimientoId\":\"m-1\",\"importe\":\"120.50\"}";

    private final BandejaDeSalida bandeja = mock(BandejaDeSalida.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final PublicadorDeOutbox publicador =
            new PublicadorDeOutbox(bandeja, rabbit, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("el mensaje lleva el JSON tal cual, el id del outbox como messageId y es persistente")
    void mensaje() {
        UUID id = UUID.randomUUID();
        Message m = PublicadorDeOutbox.mensajeDe(new EventoPendiente(id, "TransferenciaRealizada", CARGA, (short) 0));

        assertThat(new String(m.getBody(), StandardCharsets.UTF_8)).isEqualTo(CARGA);
        assertThat(m.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(m.getMessageProperties().getType()).isEqualTo("TransferenciaRealizada");
        assertThat(m.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(m.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    @DisplayName("publica con la clave core.<evento> y marca el evento como publicado")
    void publica() {
        UUID id = UUID.randomUUID();
        when(bandeja.pendientes()).thenReturn(List.of(new EventoPendiente(id, "TransferenciaRealizada", CARGA, (short) 0)));
        var mensaje = ArgumentCaptor.forClass(Message.class);

        publicador.publicarLoPendiente();

        verify(rabbit).send(eq(ConfiguracionDeMensajeria.EXCHANGE), eq("core.transferencia.realizada"), mensaje.capture());
        assertThat(mensaje.getValue().getMessageProperties().getMessageId()).isEqualTo(id.toString());
        verify(bandeja).marcarPublicado(id, AHORA);
    }

    @Test
    @DisplayName("si RabbitMQ falla, anota el fallo y sigue con los demas")
    void fallo() {
        UUID malo = UUID.randomUUID();
        UUID bueno = UUID.randomUUID();
        when(bandeja.pendientes()).thenReturn(List.of(
                new EventoPendiente(malo, "TransferenciaRealizada", CARGA, (short) 0),
                new EventoPendiente(bueno, "DepositoSimuladoRegistrado", CARGA, (short) 0)));
        doThrow(new AmqpException("caido")).when(rabbit)
                .send(anyString(), eq("core.transferencia.realizada"), any(Message.class));

        publicador.publicarLoPendiente();

        verify(bandeja).anotarFallo(eq(malo), anyString());
        verify(bandeja, never()).marcarPublicado(eq(malo), any());
        verify(rabbit).send(anyString(), eq("core.deposito.simulado.registrado"), any(Message.class));
        verify(bandeja).marcarPublicado(bueno, AHORA);
    }
}
