package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Hotfix 2.2.1: el historial de Envers con el mismo usuario que en RDS, duenio de la base pero sin
 * superusuario. Las demas pruebas corren como superusuario, que se salta los privilegios, y por eso no
 * vieron que la llave foranea hacia {@code revision_auditoria} fallaba con «permission denied».
 */
@Testcontainers
class HistorialSinSuperusuarioTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate app;

    @BeforeAll
    static void migrarComoEnRds() {
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("CREATE ROLE ayni_app LOGIN PASSWORD 'app' NOSUPERUSER");
        admin.execute("GRANT CONNECT, CREATE ON DATABASE " + POSTGRES.getDatabaseName() + " TO ayni_app");
        DriverManagerDataSource comoApp = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "ayni_app", "app");
        Flyway.configure().dataSource(comoApp).schemas("core").defaultSchema("core").load().migrate();
        app = new JdbcTemplate(comoApp);
    }

    private static Long nuevaRevision() {
        return app.queryForObject("INSERT INTO core.revision_auditoria (momento) VALUES (now()) RETURNING id", Long.class);
    }

    @Test
    @DisplayName("Sin superusuario, Envers puede guardar una version nueva")
    void guardaUnaVersionNueva() {
        Long revision = nuevaRevision();

        app.update("INSERT INTO core.cuenta_aud (id, rev, revtype) VALUES (?, ?, 1)", UUID.randomUUID(), revision);

        assertThat(app.queryForObject("SELECT count(*) FROM core.cuenta_aud WHERE rev = ?", Long.class, revision))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("El historial sigue sin poder reescribirse: el trigger rechaza el UPDATE con 42501")
    void sigueSinPoderReescribirse() {
        Long revision = nuevaRevision();

        assertThatThrownBy(() -> app.update("UPDATE core.revision_auditoria SET actor = NULL WHERE id = ?", revision))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(
                        ((SQLException) e.getMostSpecificCause()).getSQLState()).isEqualTo("42501"));
    }
}
