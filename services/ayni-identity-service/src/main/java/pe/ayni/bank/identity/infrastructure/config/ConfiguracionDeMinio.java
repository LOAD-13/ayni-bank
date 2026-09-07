package pe.ayni.bank.identity.infrastructure.config;

import io.minio.MinioClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionDeMinio {

    @Bean
    public MinioClient minioClient(
            @Value("${ayni.minio.endpoint}") String endpoint,
            @Value("${ayni.minio.access-key}") String accessKey,
            @Value("${ayni.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
