package pe.ayni.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import reactor.test.StepVerifier;

/**
 * El gateway arranca con su configuracion real: rutas, filtros y limites de tasa.
 *
 * <p>Las pruebas unitarias no lo detectan: una ambiguedad entre beans o un argumento mal
 * escrito en {@code application.yml} solo falla al levantar el contexto, y en produccion
 * eso es un despliegue revertido. No necesita Redis: la conexion se abre en la primera
 * peticion limitada, no al arrancar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueDelGatewayTest {

    @Autowired
    private RouteLocator rutas;

    @Test
    @DisplayName("arranca y carga las rutas con limite de tasa antes que las generales")
    void arrancaConSusRutas() {
        StepVerifier.create(rutas.getRoutes().map(ruta -> ruta.getId()).collectList())
                .assertNext(ids -> {
                    assertThat(ids).startsWith("limite-ingreso");
                    assertThat(ids).contains("limite-transferencias", "identity", "core-banking");
                    assertThat(ids.indexOf("limite-registro")).isLessThan(ids.indexOf("identity"));
                })
                .verifyComplete();
    }
}
