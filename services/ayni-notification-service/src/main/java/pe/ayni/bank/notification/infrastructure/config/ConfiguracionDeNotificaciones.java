package pe.ayni.bank.notification.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import pe.ayni.bank.notification.domain.model.PoliticaDeReintentos;

@Configuration
@EnableScheduling
public class ConfiguracionDeNotificaciones {

    /** UTC en todo el sistema, como en core-banking. La hora de Lima solo aparece en el texto del correo. */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    public PoliticaDeReintentos politicaDeReintentos(
            @Value("${ayni.notificaciones.reintentos.base}") Duration base,
            @Value("${ayni.notificaciones.reintentos.maximo}") int maximo) {
        return new PoliticaDeReintentos(base, maximo);
    }
}
