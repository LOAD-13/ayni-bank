package pe.ayni.bank.notification.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import pe.ayni.bank.notification.domain.port.out.EnviadorDeCorreoPort;
import pe.ayni.bank.notification.infrastructure.out.correo.EnviadorDeCorreoEnBitacora;
import pe.ayni.bank.notification.infrastructure.out.correo.EnviadorDeCorreoSes;
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
                .httpClient(UrlConnectionHttpClient.create())
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
