package pe.ayni.bank.identity.infrastructure.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.VerificationApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.invoker.ApiClient;

/**
 * Bean del cliente generado hacia kyc-service (AYNI-13 subtarea 2), con el
 * timeout de 10s que fija diseno-base.md §3.5 punto 5.
 *
 * <p>No se usa {@code @TimeLimiter} de Resilience4j: esa anotacion exige que
 * el metodo decorado devuelva {@code CompletableFuture} (o similar), y
 * {@code VerificationApi} es sincrono y bloqueante ({@code RestClient}).
 * Envolverlo en {@code @Async} solo para poder usar {@code @TimeLimiter}
 * anadiria un pool de hilos a gestionar sin necesidad: el timeout de
 * {@code RestClient} ya resuelve esto de forma nativa. Ver ADR-0020.
 */
@Configuration
public class ConfiguracionDelClienteKyc {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public VerificationApi verificationApi(@Value("${ayni.kyc.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);

        ObjectMapper mapper = ApiClient.createDefaultObjectMapper(null);
        RestClient restClient = ApiClient.buildRestClientBuilder(mapper)
                .requestFactory(requestFactory)
                .build();

        ApiClient apiClient = new ApiClient(restClient);
        apiClient.setBasePath(baseUrl);
        return new VerificationApi(apiClient);
    }
}
