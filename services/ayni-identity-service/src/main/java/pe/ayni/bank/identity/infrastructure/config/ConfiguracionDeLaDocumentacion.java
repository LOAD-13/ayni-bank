package pe.ayni.bank.identity.infrastructure.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Cabecera del contrato OpenAPI que springdoc genera del codigo en {@code /v3/api-docs}.
 *
 * <p>Solo existe con {@code AYNI_API_DOCS=true}, que pone el compose local: en produccion la
 * documentacion de la API no se publica (ADR-0033). El servidor es {@code /} y no la direccion
 * interna del servicio porque Swagger UI se sirve desde el gateway: «Probar» debe llamar al
 * gateway, que es quien valida el token, y no a una direccion de la red de Docker.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ConfiguracionDeLaDocumentacion {

    @Bean
    public OpenAPI contrato() {
        return new OpenAPI()
                .info(new Info().title("Ayni Identity Service").version("v1")
                        .description("Registro, segundo factor, sesion, recuperacion de contrasena y verificacion de identidad."))
                .servers(List.of(new Server().url("/").description("A traves del gateway")));
    }
}
