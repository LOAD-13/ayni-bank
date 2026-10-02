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
        return construir(endpoint, accessKey, secretKey);
    }

    @Bean
    public MinioClient minioClientPublico(
            @Value("${ayni.minio.endpoint-publico}") String endpointPublico,
            @Value("${ayni.minio.access-key}") String accessKey,
            @Value("${ayni.minio.secret-key}") String secretKey) {
        return construir(endpointPublico, accessKey, secretKey);
    }

    private MinioClient construir(String endpoint, String accessKey, String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                // Sin region explicita, el SDK la resuelve con una llamada de
                // red al construir/firmar (GetBucketLocation). MinIO no es
                // multi-region: fijarla evita ese round-trip en cada firma.
                .region("us-east-1")
                .build();
    }
}
