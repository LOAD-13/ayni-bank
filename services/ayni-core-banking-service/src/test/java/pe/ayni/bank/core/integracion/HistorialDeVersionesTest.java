package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.envers.AuditReaderFactory;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.infrastructure.out.crypto.CifradorDePan;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeTarjetas;
import pe.ayni.bank.core.infrastructure.out.persistence.RevisionDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.TarjetaEntity;

/**
 * HU-15, AYNI-279: Envers guarda cada version de la tarjeta con su actor, el historial no se puede
 * reescribir y se puede pedir el estado en una fecha pasada.
 *
 * <p>Sin transaccion de la prueba: Envers escribe las versiones al confirmar, y una transaccion que
 * termina en rollback no dejaria ninguna.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AdaptadorRepositorioDeTarjetas.class, CifradorDePan.class})
@TestPropertySource(properties = "ayni.cifrado.clave=YXluaS1jbGF2ZS1kZS1kZXNhcnJvbGxvLTMyYnl0ZXM=")
class HistorialDeVersionesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // La cuenta tecnica de fondeo existe desde V3 y tiene su version inicial desde V18.
    private static final UUID CUENTA = UUID.fromString("f0de0000-0000-4000-8000-000000000001");

    @Autowired
    private AdaptadorRepositorioDeTarjetas tarjetas;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory emf;

    @AfterEach
    void sinPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void comoUsuario(UUID usuario) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/tarjetas/mia/congelamiento");
        peticion.addHeader("X-Ayni-Usuario", usuario.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
    }

    private Tarjeta emitida() {
        Tarjeta tarjeta = Tarjeta.emitir(CUENTA, UUID.randomUUID(), new Pan("4999990000000007"),
                Instant.parse("2026-11-20T15:00:00Z"));
        jdbc.update("DELETE FROM core.control_tarjeta");
        jdbc.update("DELETE FROM core.tarjeta");
        tarjetas.guardar(tarjeta);
        return tarjeta;
    }

    private static String estadoSql(DataAccessException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql ? sql.getSQLState() : null;
    }

    @Test
    @DisplayName("Congelar la tarjeta deja una version nueva con el actor de la sesion, y el PAN no se versiona")
    void versionConActor() {
        Tarjeta tarjeta = emitida();
        UUID titular = tarjeta.usuarioId();

        comoUsuario(titular);
        tarjetas.actualizarEstado(tarjeta.id(), EstadoDeTarjeta.CONGELADA);

        List<Map<String, Object>> versiones = jdbc.queryForList("""
                SELECT a.revtype::int AS revtype, a.estado, r.actor
                FROM core.tarjeta_aud a JOIN core.revision_auditoria r ON r.id = a.rev
                WHERE a.id = ? ORDER BY a.rev""", tarjeta.id());
        assertThat(versiones).hasSize(2);
        assertThat(versiones.get(0)).containsEntry("revtype", 0).containsEntry("estado", "ACTIVA");
        assertThat(versiones.get(1)).containsEntry("revtype", 1).containsEntry("estado", "CONGELADA")
                .containsEntry("actor", titular);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = 'core' AND table_name = 'tarjeta_aud'", String.class))
                .doesNotContain("pan_cifrado", "pan_huella");
    }

    @Test
    @DisplayName("El historial devuelve el estado que tenia la tarjeta en una fecha pasada")
    void estadoEnUnaFecha() {
        Tarjeta tarjeta = emitida();
        // El momento exacto de la version de alta: lo que habia "entonces".
        Instant antesDeCongelar = jdbc.queryForObject("""
                SELECT r.momento FROM core.tarjeta_aud a JOIN core.revision_auditoria r ON r.id = a.rev
                WHERE a.id = ? AND a.revtype = 0""", java.sql.Timestamp.class, tarjeta.id()).toInstant();
        // Que el cambio caiga en un milisegundo posterior: si no, la fecha casaria con las dos versiones.
        while (!Instant.now().isAfter(antesDeCongelar.plusMillis(1))) {
            Thread.onSpinWait();
        }
        tarjetas.actualizarEstado(tarjeta.id(), EstadoDeTarjeta.CONGELADA);

        try (var em = emf.createEntityManager()) {
            var lector = AuditReaderFactory.get(em);
            Number revision = lector.getRevisionNumberForDate(java.util.Date.from(antesDeCongelar));
            assertThat(lector.find(TarjetaEntity.class, tarjeta.id(), revision)).isNotNull();
            assertThat(jdbc.queryForObject("SELECT estado FROM core.tarjeta_aud WHERE id = ? AND rev <= ?"
                    + " ORDER BY rev DESC LIMIT 1", String.class, tarjeta.id(), revision.longValue()))
                    .isEqualTo("ACTIVA");
            RevisionDeAuditoria ultima = lector.findRevision(RevisionDeAuditoria.class,
                    lector.getRevisions(TarjetaEntity.class, tarjeta.id()).getLast());
            assertThat(ultima.getMomento()).isAfterOrEqualTo(antesDeCongelar);
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM core.tarjeta WHERE id = ?", String.class, tarjeta.id()))
                .isEqualTo("CONGELADA");
    }

    @Test
    @DisplayName("Las cuentas que ya existian tienen su version inicial, y el historial no se puede reescribir")
    void historialInalterable() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.cuenta_aud WHERE id = ? AND revtype = 0",
                Integer.class, CUENTA)).isOne();

        assertThatThrownBy(() -> jdbc.update("UPDATE core.cuenta_aud SET estado = 'CERRADA' WHERE id = ?", CUENTA))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core.revision_auditoria"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThat(jdbc.queryForObject("SELECT estado FROM core.cuenta_aud WHERE id = ? AND revtype = 0",
                String.class, CUENTA)).isEqualTo("ACTIVA");
    }
}
