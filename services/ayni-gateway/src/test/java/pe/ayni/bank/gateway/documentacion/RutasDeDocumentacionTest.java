package pe.ayni.bank.gateway.documentacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * Con AYNI_API_DOCS encendido, como en el compose local: el gateway arranca y crea una ruta hacia el
 * contrato de cada servicio. Arrancar el contexto entero es lo que detecta errores de configuracion
 * que solo aparecen con la documentacion encendida.
 */
@SpringBootTest(properties = {"springdoc.swagger-ui.enabled=true", "springdoc.api-docs.enabled=true"})
class RutasDeDocumentacionTest {

    @Autowired
    private RouteLocator rutas;

    @Test
    @DisplayName("Con la documentacion encendida existen las rutas hacia los contratos de identity, core-banking y kyc")
    void rutasHaciaLosContratos() {
        Map<String, String> destinos = rutas.getRoutes().collectList().block().stream()
                .filter(r -> r.getId().startsWith("docs-"))
                .collect(Collectors.toMap(Route::getId, r -> r.getUri().toString()));

        assertThat(destinos).containsOnlyKeys("docs-identity", "docs-core-banking", "docs-kyc");
        assertThat(destinos).containsEntry("docs-kyc", "http://ayni-kyc-service:8000");
    }
}
