package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTiempo;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;

/**
 * HU-15, escenario 1: la pista de auditoria es de solo insercion contra PostgreSQL real, y cada
 * registro lleva actor, accion, recurso, IP, user-agent, resultado y momento.
 *
 * <p>Sin transaccion de la prueba: un UPDATE rechazado abortaria la transaccion entera y las
 * comprobaciones siguientes no podrian ni leer.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AdaptadorPistaDeAuditoria.class, ConfiguracionDeTiempo.class})
class PistaDeAuditoriaInalterableTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AdaptadorPistaDeAuditoria auditoria;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sinPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void enUnaPeticion() {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/transferencias");
        peticion.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        peticion.addHeader("User-Agent", "Mozilla/5.0 (prueba de auditoria)");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
    }

    private Map<String, Object> eventoDe(UUID usuario) {
        return jdbc.queryForMap("SELECT tipo, usuario_id, movimiento_id, ip, agente, recurso, resultado, ocurrido_en"
                + " FROM core.evento_auditoria WHERE usuario_id = ?", usuario);
    }

    private static String estadoSql(DataAccessException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql ? sql.getSQLState() : null;
    }

    @Test
    @DisplayName("Cada registro lleva actor, accion, recurso, IP, user-agent, resultado y momento")
    void registroCompleto() {
        enUnaPeticion();
        UUID usuario = UUID.randomUUID();
        UUID movimiento = UUID.randomUUID();

        auditoria.registrar(TipoDeEventoDeOperacion.TRANSFERENCIA_REALIZADA, usuario, movimiento);

        assertThat(eventoDe(usuario))
                .containsEntry("tipo", "TRANSFERENCIA_REALIZADA")
                .containsEntry("movimiento_id", movimiento)
                .containsEntry("ip", "203.0.113.7")
                .containsEntry("agente", "Mozilla/5.0 (prueba de auditoria)")
                .containsEntry("recurso", "POST /api/v1/transferencias")
                .containsEntry("resultado", "EXITO")
                .containsKey("ocurrido_en");
    }

    @Test
    @DisplayName("Un rechazo queda con resultado RECHAZADO, y sin peticion no hay IP ni recurso")
    void rechazoSinPeticion() {
        UUID usuario = UUID.randomUUID();

        auditoria.registrarRechazo(usuario, MotivoDeRechazo.SALDO_INSUFICIENTE);

        assertThat(eventoDe(usuario))
                .containsEntry("resultado", "RECHAZADO")
                .containsEntry("ip", null)
                .containsEntry("recurso", null);
    }

    @Test
    @DisplayName("Escenario 1: UPDATE, DELETE y TRUNCATE se rechazan por falta de permisos y el registro sigue intacto")
    void soloInsercion() {
        UUID usuario = UUID.randomUUID();
        auditoria.registrar(TipoDeEventoDeOperacion.DEPOSITO_SIMULADO, usuario, UUID.randomUUID());
        Map<String, Object> original = eventoDe(usuario);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE core.evento_auditoria SET tipo = 'OPERACION_REPETIDA' WHERE usuario_id = ?", usuario))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core.evento_auditoria WHERE usuario_id = ?", usuario))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE core.evento_auditoria"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));

        assertThat(eventoDe(usuario)).isEqualTo(original);
    }
}
