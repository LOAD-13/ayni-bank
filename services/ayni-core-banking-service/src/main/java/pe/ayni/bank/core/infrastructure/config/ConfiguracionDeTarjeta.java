package pe.ayni.bank.core.infrastructure.config;

import java.security.SecureRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.ayni.bank.core.domain.service.CalculadoraDeCvv;
import pe.ayni.bank.core.domain.service.GeneradorDePan;
import pe.ayni.bank.core.infrastructure.out.crypto.CifradorDePan;

/** Los servicios de dominio de HU-09, que no conocen Spring, con lo que necesitan. */
@Configuration
public class ConfiguracionDeTarjeta {

    @Bean
    public GeneradorDePan generadorDePan() {
        return new GeneradorDePan(new SecureRandom());
    }

    @Bean
    public CalculadoraDeCvv calculadoraDeCvv(CifradorDePan cifrador) {
        return new CalculadoraDeCvv(cifrador.claveDeCvv());
    }
}
