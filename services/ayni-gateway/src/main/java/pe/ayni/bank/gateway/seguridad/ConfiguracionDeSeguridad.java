package pe.ayni.bank.gateway.seguridad;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionDeSeguridad {

    @Bean
    public VerificadorDeTokens verificadorDeTokens(@Value("${ayni.jwt.clave}") String clave) {
        return new VerificadorDeTokens(clave, Clock.systemUTC());
    }

    @Bean
    public FiltroDeAutenticacion filtroDeAutenticacion(
            VerificadorDeTokens verificador,
            @Value("${ayni.seguridad.rutas-protegidas}") List<String> rutasProtegidas) {
        return new FiltroDeAutenticacion(verificador, rutasProtegidas);
    }
}
