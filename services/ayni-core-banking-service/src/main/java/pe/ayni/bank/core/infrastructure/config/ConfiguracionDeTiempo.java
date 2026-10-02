package pe.ayni.bank.core.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj del sistema como bean.
 *
 * <p>Nadie llama a {@code Instant.now()} directamente. Con el reloj inyectado, una prueba
 * puede fijar el instante y comprobar reglas que dependen del tiempo sin dormir hilos ni
 * depender de cuando se ejecute.
 */
@Configuration
public class ConfiguracionDeTiempo {

    @Bean
    public Clock reloj() {
        // UTC en todo el sistema. Guardar horas locales obliga a razonar sobre husos y
        // cambios de hora en cada consulta, y en un sistema financiero eso acaba en un
        // movimiento fechado el dia equivocado.
        return Clock.systemUTC();
    }
}
