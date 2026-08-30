package pe.ayni.bank.core.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia de RabbitMQ para el esqueleto ambulante.
 *
 * <p>Un <em>topic exchange</em> y no una cola directa: los eventos de dominio los puede
 * querer oir mas de un servicio —hoy core-banking abre la cuenta, manana notificacion
 * avisara al cliente—, y con un exchange cada uno se engancha con su propia cola sin que
 * el que publica tenga que enterarse.
 */
@Configuration
public class ConfiguracionDeMensajeria {

    public static final String EXCHANGE = "ayni.eventos";
    public static final String COLA_SOLICITUDES_APROBADAS = "core.solicitud-aprobada";
    public static final String CLAVE_SOLICITUD_APROBADA = "identity.solicitud.aprobada";

    @Bean
    public TopicExchange exchangeDeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue colaDeSolicitudesAprobadas() {
        // Duradera: si RabbitMQ se reinicia, los eventos pendientes siguen ahi. Una cola
        // efimera perderia aperturas de cuenta en cada reinicio del broker.
        return QueueBuilder.durable(COLA_SOLICITUDES_APROBADAS).build();
    }

    @Bean
    public Binding enlaceDeSolicitudesAprobadas() {
        return BindingBuilder.bind(colaDeSolicitudesAprobadas())
                .to(exchangeDeEventos())
                .with(CLAVE_SOLICITUD_APROBADA);
    }

    @Bean
    public MessageConverter conversorDeMensajes(ObjectMapper mapeador) {
        // JSON y no la serializacion de Java: los mensajes los tienen que poder leer
        // servicios escritos en Python, y un objeto serializado de Java no lo lee nadie
        // mas que Java.
        return new Jackson2JsonMessageConverter(mapeador);
    }
}
