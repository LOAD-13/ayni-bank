package pe.ayni.bank.identity.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El tiempo entra por inyeccion, no por {@code Instant.now()} repartido por el codigo.
 *
 * <p>Con esto, una prueba fija el momento y comprueba caducidades, vigencias y marcas
 * temporales sin esperar ni dormir hilos. Con llamadas directas al reloj del sistema, esas
 * pruebas o no se escriben o salen intermitentes.
 *
 * <p>UTC y no la zona del servidor: un contenedor que arranca con otra zona horaria no
 * puede cambiar el momento en que se otorgo un consentimiento.
 */
@Configuration
public class ConfiguracionDeTiempo {

    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
