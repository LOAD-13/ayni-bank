package pe.ayni.bank.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** El contrato OpenAPI del servicio no se publica por defecto (ADR-0033). */
class DocumentacionDeLaApiTest {

    @Test
    @DisplayName("Sin AYNI_API_DOCS, /v3/api-docs y Swagger UI estan apagados")
    void apagadaPorDefecto() throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(entorno.getPropertySources()::addLast);

        assertThat(entorno.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(entorno.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }
}
