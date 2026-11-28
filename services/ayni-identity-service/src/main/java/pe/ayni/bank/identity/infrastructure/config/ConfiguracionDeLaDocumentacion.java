package pe.ayni.bank.identity.infrastructure.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Cabecera del contrato OpenAPI que springdoc genera del codigo en {@code /v3/api-docs}.
 *
 * <p>Solo existe con {@code AYNI_API_DOCS=true}, que pone el compose local: en produccion la
 * documentacion de la API no se publica (ADR-0033). El servidor es {@code /} y no la direccion
 * interna del servicio porque Swagger UI se sirve desde el gateway: «Probar» debe llamar al
 * gateway, que es quien valida el token, y no a una direccion de la red de Docker.
 *
 * <p>El esquema {@code bearerAuth} hace que Swagger UI muestre «Authorize»: con el token de acceso
 * que devuelve {@code POST /api/v1/sesion/segundo-factor} se prueban las rutas protegidas.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class ConfiguracionDeLaDocumentacion {

    static final String ESQUEMA = "bearerAuth";

    @Bean
    public OpenAPI contrato() {
        return new OpenAPI()
                .info(new Info().title("Ayni Identity Service").version("v1")
                        .description("Registro, segundo factor, sesion, recuperacion de contrasena y verificacion de identidad."))
                .servers(List.of(new Server().url("/").description("A traves del gateway")))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Token de acceso de POST /api/v1/sesion/segundo-factor")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
