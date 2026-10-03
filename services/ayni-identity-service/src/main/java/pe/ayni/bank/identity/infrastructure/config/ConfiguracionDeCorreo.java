package pe.ayni.bank.identity.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import pe.ayni.bank.identity.infrastructure.out.correo.EnviadorDeCorreo;
import pe.ayni.bank.identity.infrastructure.out.correo.EnviadorDeCorreoSes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** El cliente de Amazon SES, solo en produccion. */
@Configuration
@Profile("prod")
public class ConfiguracionDeCorreo {

    @Bean(destroyMethod = "close")
    public SesV2Client sesV2Client(@Value("${ayni.correo.region:us-east-1}") String region) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    @Bean
    public EnviadorDeCorreo enviadorDeCorreo(SesV2Client ses,
                                             @Value("${ayni.correo.remitente}") String remitente) {
        return new EnviadorDeCorreoSes(ses, remitente);
    }
}
