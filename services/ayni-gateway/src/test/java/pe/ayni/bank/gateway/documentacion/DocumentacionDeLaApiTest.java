package pe.ayni.bank.gateway.documentacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * La documentacion de la API nunca se publica por defecto (ADR-0033). Si alguien la enciende por
 * error en la configuracion o en el compose de produccion, la integracion se para aqui.
 */
class DocumentacionDeLaApiTest {

    /** Lo que vale cada propiedad sin ninguna variable de entorno: el valor por defecto real. */
    private static StandardEnvironment sinVariables() throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(entorno.getPropertySources()::addLast);
        return entorno;
    }

    @Test
    @DisplayName("Sin AYNI_API_DOCS, Swagger UI y el contrato del gateway estan apagados")
    void apagadaPorDefecto() throws IOException {
        StandardEnvironment entorno = sinVariables();

        assertThat(entorno.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(entorno.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("Sin AYNI_API_DOCS no se crean las rutas hacia los contratos de los servicios")
    void sinRutasPorDefecto() {
        new ApplicationContextRunner()
                .withUserConfiguration(RutasDeDocumentacion.class)
                .run(contexto -> assertThat(contexto).doesNotHaveBean(RouteLocator.class));
    }

    @Test
    @DisplayName("El compose de produccion no enciende la documentacion de la API")
    void produccionNoLaEnciende() throws IOException {
        String produccion = Files.readString(Path.of("../../infra/docker/docker-compose.prod.yml"));

        assertThat(produccion).doesNotContain("API_DOCS");
    }
}
