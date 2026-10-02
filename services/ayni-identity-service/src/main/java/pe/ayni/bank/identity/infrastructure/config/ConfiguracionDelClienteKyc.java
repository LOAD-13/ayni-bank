package pe.ayni.bank.identity.infrastructure.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.DocumentosApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.invoker.ApiClient;

/**
 * Bean del cliente generado hacia kyc-service (AYNI-13 subtarea 2), con el
 * timeout de 10s que fija diseno-base.md §3.5 punto 5.
 *
 * <p>No se usa {@code @TimeLimiter} de Resilience4j: esa anotacion exige que
 * el metodo decorado devuelva {@code CompletableFuture} (o similar), y
 * {@code DocumentosApi} es sincrono y bloqueante ({@code RestClient}).
 * Envolverlo en {@code @Async} solo para poder usar {@code @TimeLimiter}
 * anadiria un pool de hilos a gestionar sin necesidad: el timeout de
 * {@code RestClient} ya resuelve esto de forma nativa. Ver ADR-0020.
 */
@Configuration
public class ConfiguracionDelClienteKyc {

    /**
     * diseno-base.md §3.5 punto 5: la respuesta de kyc-service se espera 10 s como maximo.
     * Aplica a la evaluacion de cada foto, que es OpenCV y responde en milisegundos.
     */
    private static final Duration TIMEOUT_DE_LECTURA = Duration.ofSeconds(10);

    /**
     * Conectar es casi instantaneo dentro de la red de Docker: si tarda, el servicio no esta.
     * Con 2 s, un kyc-service caido se detecta pronto y el reintento no se come la espera.
     */
    private static final Duration TIMEOUT_DE_CONEXION = Duration.ofSeconds(2);

    /** Cliente de la evaluacion de cada foto, con el timeout de 10 s. */
    @Bean
    @Primary
    public DocumentosApi documentosApi(@Value("${ayni.kyc.base-url}") String baseUrl) {
        return crear(baseUrl, TIMEOUT_DE_LECTURA);
    }

    /**
     * Cliente de la lectura por OCR, con su propio timeout.
     *
     * <p>PaddleOCR sobre las dos caras del DNI tarda ~9-11 s en un portatil (medido en la
     * prueba de punta a punta) y mas en la Raspberry Pi 5. Con 10 s, una lectura correcta se
     * cortaria y la solicitud acabaria en revision manual. Ver ADR-0026.
     */
    @Bean
    public DocumentosApi documentosApiDeExtraccion(
            @Value("${ayni.kyc.base-url}") String baseUrl,
            @Value("${ayni.kyc.timeout-de-extraccion}") Duration timeoutDeExtraccion) {
        return crear(baseUrl, timeoutDeExtraccion);
    }

    private static DocumentosApi crear(String baseUrl, Duration timeoutDeLectura) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_DE_CONEXION);
        requestFactory.setReadTimeout(timeoutDeLectura);

        ObjectMapper mapper = ApiClient.createDefaultObjectMapper(null);
        RestClient restClient = ApiClient.buildRestClientBuilder(mapper)
                .requestFactory(requestFactory)
                .build();

        ApiClient apiClient = new ApiClient(restClient);
        apiClient.setBasePath(baseUrl);
        return new DocumentosApi(apiClient);
    }
}
