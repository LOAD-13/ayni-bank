package pe.ayni.bank.notification.infrastructure.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import pe.ayni.bank.notification.domain.port.out.EnviadorDeCorreoPort;
import pe.ayni.bank.notification.infrastructure.out.correo.EnviadorDeCorreoEnBitacora;
import pe.ayni.bank.notification.infrastructure.out.correo.EnviadorDeCorreoSes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** Amazon SES en produccion; en cualquier otro entorno, el correo solo se anota en el log. */
@Configuration
public class ConfiguracionDeCorreo {

    @Bean(destroyMethod = "close")
    @Profile("prod")
    public SesV2Client sesV2Client(@Value("${ayni.correo.region:us-east-1}") String region) {
        return SesV2Client.builder()
                .region(Region.of(region))
                // Sin estos limites, una SES que no responde deja el hilo colgado sin fin
                // (ISO/IEC 25010, interoperabilidad): 3 s para conectar, 5 s por intento y
                // 12 s en total con los reintentos del SDK. Al vencer, el SDK lanza
                // ApiCallTimeoutException y el envio se trata como cualquier otro fallo.
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(5))
                        .build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .apiCallTimeout(Duration.ofSeconds(12))
                        .build())
                .build();
    }

    @Bean
    @Profile("prod")
    public EnviadorDeCorreoPort enviadorDeCorreoSes(SesV2Client ses,
                                                   @Value("${ayni.correo.remitente}") String remitente) {
        return new EnviadorDeCorreoSes(ses, remitente);
    }

    @Bean
    @Profile("!prod")
    public EnviadorDeCorreoPort enviadorDeCorreoEnBitacora() {
        return new EnviadorDeCorreoEnBitacora();
    }
}
