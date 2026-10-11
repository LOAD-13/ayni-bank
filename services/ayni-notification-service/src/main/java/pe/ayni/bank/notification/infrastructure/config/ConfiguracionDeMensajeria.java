package pe.ayni.bank.notification.infrastructure.config;

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
 * Las colas de HU-13 sobre el exchange comun {@code ayni.eventos}.
 *
 * <p>Una cola por tipo de evento: cada escuchador recibe un solo contrato y un mensaje
 * que no se puede leer no bloquea a los demas. Todas duraderas, como en core-banking.
 */
@Configuration
public class ConfiguracionDeMensajeria {

    public static final String EXCHANGE = "ayni.eventos";

    public static final String COLA_TITULARES = "notification.titular-activado";
    public static final String COLA_TRANSFERENCIAS = "notification.transferencia-realizada";
    public static final String COLA_DEPOSITOS = "notification.deposito-registrado";
    public static final String COLA_INTERESES = "notification.interes-capitalizado";

    static final String CLAVE_TITULAR_ACTIVADO = "identity.titular.activado";
    static final String CLAVE_TRANSFERENCIA = "core.transferencia.realizada";
    static final String CLAVE_DEPOSITO = "core.deposito.simulado.registrado";
    static final String CLAVE_INTERES = "core.interes.capitalizado";

    @Bean
    public TopicExchange exchangeDeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue colaDeTitulares() {
        return QueueBuilder.durable(COLA_TITULARES).build();
    }

    @Bean
    public Queue colaDeTransferencias() {
        return QueueBuilder.durable(COLA_TRANSFERENCIAS).build();
    }

    @Bean
    public Queue colaDeDepositos() {
        return QueueBuilder.durable(COLA_DEPOSITOS).build();
    }

    @Bean
    public Queue colaDeIntereses() {
        return QueueBuilder.durable(COLA_INTERESES).build();
    }

    @Bean
    public Binding enlaceDeTitulares() {
        return BindingBuilder.bind(colaDeTitulares()).to(exchangeDeEventos()).with(CLAVE_TITULAR_ACTIVADO);
    }

    @Bean
    public Binding enlaceDeTransferencias() {
        return BindingBuilder.bind(colaDeTransferencias()).to(exchangeDeEventos()).with(CLAVE_TRANSFERENCIA);
    }

    @Bean
    public Binding enlaceDeDepositos() {
        return BindingBuilder.bind(colaDeDepositos()).to(exchangeDeEventos()).with(CLAVE_DEPOSITO);
    }

    @Bean
    public Binding enlaceDeIntereses() {
        return BindingBuilder.bind(colaDeIntereses()).to(exchangeDeEventos()).with(CLAVE_INTERES);
    }

    @Bean
    public MessageConverter conversorDeMensajes(ObjectMapper mapeador) {
        // El contrato entre servicios es el JSON: cada escuchador declara su propio record y
        // el conversor lo deduce del parametro, sin depender de clases de otro servicio.
        return new Jackson2JsonMessageConverter(mapeador);
    }
}
