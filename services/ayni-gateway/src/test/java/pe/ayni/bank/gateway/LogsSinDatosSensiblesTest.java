package pe.ayni.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * HU-15, escenario 4 (AYNI-283): el patron de los logs de este servicio enmascara los datos
 * sensibles antes de escribirlos. Se lee de application.yml, el mismo que usa el servicio.
 */
class LogsSinDatosSensiblesTest {

    private static String escrito(String mensaje) throws IOException {
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                .load("app", new ClassPathResource("application.yml"));
        String patron = (String) fuentes.get(0).getProperty("logging.pattern.console");
        assertThat(patron).as("logging.pattern.console en application.yml").isNotBlank();

        LoggerContext contexto = new LoggerContext();
        // El patron imprime el traceId del MDC: un contexto nuevo no trae adaptador de MDC.
        contexto.setMDCAdapter(new LogbackMDCAdapter());
        PatternLayout disposicion = new PatternLayout();
        disposicion.setContext(contexto);
        disposicion.getInstanceConverterMap().put("wEx", WhitespaceThrowableProxyConverter::new);
        disposicion.setPattern(patron);
        disposicion.start();
        LoggingEvent evento = new LoggingEvent("prueba", contexto.getLogger("prueba"), Level.INFO, mensaje, null, null);
        return disposicion.doLayout(evento);
    }

    @Test
    void enmascara_el_pan_y_cualquier_numero_largo() throws IOException {
        String linea = escrito("Pago con 4999990000000007 aceptado");
        assertThat(linea).contains("4999****0007").doesNotContain("4999990000000007");
    }

    @Test
    void enmascara_tokens_jwt_y_bearer() throws IOException {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmEifQ.c2lnbmF0dXJh";
        assertThat(escrito("token=" + jwt)).contains("[TOKEN]").doesNotContain("eyJzdWIiOiJhbmEifQ");
        assertThat(escrito("Authorization: Bearer abc.def-123")).contains("Bearer [TOKEN]").doesNotContain("abc.def-123");
    }

    @Test
    void enmascara_contrasenas_y_dni() throws IOException {
        assertThat(escrito("contrasena=Secreta!2026 usuario=ana")).contains("contrasena=***").doesNotContain("Secreta!2026");
        assertThat(escrito("{\"password\": \"Otra#Clave1\"}")).doesNotContain("Otra#Clave1");
        assertThat(escrito("DNI 45678912 verificado")).contains("DNI ********").doesNotContain("45678912");
    }

    @Test
    void deja_igual_lo_que_no_es_sensible() throws IOException {
        String id = "5d1f3c9e-1b2a-4c3d-8e9f-0a1b2c3d4e5f";
        assertThat(escrito("Movimiento registrado. movimientoId=" + id + " importe=370.00"))
                .contains(id).contains("importe=370.00");
    }
}
