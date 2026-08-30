package pe.ayni.bank.identity.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lo minimo de RabbitMQ que necesita identidad: el exchange y el conversor.
 *
 * <p>Aqui no se declara ninguna cola. Quien publica no debe saber quien escucha —esa es la
 * razon de usar un exchange—, y crear la cola del consumidor desde el productor invertiria
 * la dependencia: identidad tendria que enterarse cada vez que otro servicio se suscribe.
 */
@Configuration
public class ConfiguracionDeMensajeria {

    public static final String EXCHANGE = "ayni.eventos";
    public static final String CLAVE_SOLICITUD_APROBADA = "identity.solicitud.aprobada";

    @Bean
    public TopicExchange exchangeDeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter conversorDeMensajes(ObjectMapper mapeador) {
        // JSON y no la serializacion de Java: los mensajes los tiene que poder leer un
        // servicio escrito en Python, y un objeto serializado de Java no lo lee nadie mas.
        return new Jackson2JsonMessageConverter(mapeador);
    }
}
