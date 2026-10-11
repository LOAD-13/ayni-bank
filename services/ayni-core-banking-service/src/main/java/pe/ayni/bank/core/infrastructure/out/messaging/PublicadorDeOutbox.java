package pe.ayni.bank.core.infrastructure.out.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.core.infrastructure.out.persistence.BandejaDeSalida;

/**
 * Envía a RabbitMQ lo que espera en la bandeja de salida. Segunda mitad de ADR-0003.
 *
 * <p><strong>Este es el componente que hace que el escenario 4 de HU-05 funcione.</strong>
 * Si el broker está caído, el envío falla, la marca de publicado no se pone y el evento
 * sigue ahí; en la siguiente pasada se vuelve a intentar. La cuenta, mientras tanto, existe
 * y es coherente, porque se creó en una transacción que no dependía de RabbitMQ para nada.
 *
 * <p><strong>Consecuencia que hay que aceptar:</strong> esto garantiza entrega «al menos
 * una vez», no «exactamente una». Si el envío sale bien y el proceso muere antes de marcar
 * la fila, el evento se manda dos veces. Por eso quien lo consume tiene que ser idempotente
 * —y lo es, a través de {@code operacion_idempotente}—. Perseguir el «exactamente una vez»
 * entre dos sistemas sin transacción común es perseguir algo que no existe.
 */
@Component
public class PublicadorDeOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeOutbox.class);

    /**
     * Cada dos segundos. Es el retardo máximo que se le añade a un evento en el camino
     * feliz, y para una apertura de cuenta es imperceptible.
     */
    private static final long PERIODO_MS = 2000;

    private final BandejaDeSalida bandeja;
    private final RabbitTemplate rabbit;
    private final Clock reloj;

    public PublicadorDeOutbox(BandejaDeSalida bandeja, RabbitTemplate rabbit, Clock reloj) {
        this.bandeja = bandeja;
        this.rabbit = rabbit;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelay = PERIODO_MS)
    @Transactional
    public void publicarLoPendiente() {
        List<BandejaDeSalida.EventoPendiente> pendientes = bandeja.pendientes();
        if (pendientes.isEmpty()) {
            return;
        }

        for (BandejaDeSalida.EventoPendiente evento : pendientes) {
            try {
                rabbit.send(
                        ConfiguracionDeMensajeria.EXCHANGE,
                        claveDeEncaminamiento(evento.tipoEvento()),
                        mensajeDe(evento));

                bandeja.marcarPublicado(evento.id(), reloj.instant());
            } catch (RuntimeException fallo) {
                // No se relanza: un evento que falla no debe impedir que salgan los demás,
                // y sobre todo no debe deshacer las marcas de los que ya salieron en esta
                // misma pasada.
                bandeja.anotarFallo(evento.id(), fallo.getMessage());
                log.warn("No se pudo publicar el evento {} ({} intentos). Se reintentara.",
                        evento.id(), evento.intentos() + 1);
            }
        }
    }

    /**
     * {@code CuentaAperturada} viaja como {@code core.cuenta.aperturada}.
     *
     * <p>La clave se deriva del nombre del evento en lugar de guardarla en la tabla: así no
     * hay dos sitios donde se pueda escribir mal la misma cosa.
     */
    /**
     * La carga ya es JSON: se envia tal cual, en bytes. Con {@code convertAndSend} el
     * conversor de Jackson la volvia a serializar y llegaba como una cadena JSON escapada.
     *
     * <p>El {@code messageId} es el id del outbox: es el {@code eventId} con el que los
     * consumidores descartan las entregas repetidas (HU-13). RabbitMQ entrega al menos una
     * vez, asi que la idempotencia la pone quien consume.
     */
    static Message mensajeDe(BandejaDeSalida.EventoPendiente evento) {
        return MessageBuilder.withBody(evento.carga().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(evento.id().toString())
                .setType(evento.tipoEvento())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    private static String claveDeEncaminamiento(String tipoEvento) {
        return "core." + tipoEvento
                .replaceAll("([a-z])([A-Z])", "$1.$2")
                .toLowerCase();
    }
}
