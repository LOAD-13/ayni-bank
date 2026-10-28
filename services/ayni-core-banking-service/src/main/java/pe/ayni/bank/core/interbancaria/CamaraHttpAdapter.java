package pe.ayni.bank.core.interbancaria;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Component
public class CamaraHttpAdapter implements CamaraPort {

    private final RestClient http;

    public CamaraHttpAdapter(
        @Value("${ayni.camara.url}") String url,
        RestClient.Builder builder
    ) {
        this.http = builder.baseUrl(url).build();
    }

    @Override
    @Retry(name = "camara")
    @CircuitBreaker(name = "camara")
    public EstadoInterbancario enviar(
        UUID id,
        String cci,
        String importe
    ) {

        CamaraSimuladaController.Respuesta respuesta =
            http.post()
                .uri("/transferencias")
                .body(new CamaraSimuladaController.Envio(
                    id, cci, importe
                ))
                .retrieve()
                .body(CamaraSimuladaController.Respuesta.class);

        if (respuesta == null || respuesta.estado() == null) {
            throw new IllegalStateException(
                "Respuesta de camara vacia"
            );
        }

        return respuesta.estado();
    }

    @Override
    @Retry(name = "camara")
    @CircuitBreaker(name = "camara")
    public EstadoInterbancario consultar(UUID id) {

        CamaraSimuladaController.Respuesta respuesta =
            http.get()
                .uri("/transferencias/{id}", id)
                .retrieve()
                .body(CamaraSimuladaController.Respuesta.class);

        if (respuesta == null || respuesta.estado() == null) {
            throw new IllegalStateException(
                "Respuesta de camara vacia"
            );
        }

        return respuesta.estado();
    }
}
