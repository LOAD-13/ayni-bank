package pe.ayni.bank.core.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

class InterceptorDeAuditoriaTest {

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void guarda_ip_del_cliente_agente_y_la_ruta_sin_identificadores() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/movimientos/7f3c");
        peticion.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/movimientos/{id}");
        peticion.addHeader("X-Forwarded-For", "198.51.100.4, 10.0.0.2");
        peticion.addHeader("User-Agent", "x".repeat(300));

        assertThat(new InterceptorDeAuditoria().preHandle(peticion, new MockHttpServletResponse(), new Object())).isTrue();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

        assertThat(DatosDeLaPeticion.actuales()).hasValueSatisfying(datos -> {
            assertThat(datos.ip()).isEqualTo("198.51.100.4");
            assertThat(datos.agente()).hasSize(256);
            assertThat(datos.recurso()).isEqualTo("GET /api/v1/movimientos/{id}");
        });
    }

    @Test
    void sin_cabecera_reenviada_usa_la_ip_de_la_conexion() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/depositos");
        peticion.setRemoteAddr("192.0.2.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));

        assertThat(DatosDeLaPeticion.actuales()).hasValueSatisfying(datos -> {
            assertThat(datos.ip()).isEqualTo("192.0.2.9");
            assertThat(datos.agente()).isNull();
            assertThat(datos.recurso()).isEqualTo("POST /api/v1/depositos");
        });
    }

    @Test
    void fuera_de_una_peticion_no_hay_datos() {
        assertThat(DatosDeLaPeticion.actuales()).isEmpty();
    }
}
