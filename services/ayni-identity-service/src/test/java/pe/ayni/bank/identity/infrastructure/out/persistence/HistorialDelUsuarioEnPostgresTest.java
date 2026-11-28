package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.Usuario;
import pe.ayni.bank.identity.infrastructure.config.ConfiguracionDeTiempo;

/**
 * HU-15, AYNI-279: Envers guarda cada version de los datos de la persona con su actor, sin la
 * contrasena, y el historial no se puede reescribir.
 *
 * <p>Sin transaccion de la prueba: Envers escribe las versiones al confirmar.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AdaptadorRepositorioDeUsuarios.class, ConfiguracionDeTiempo.class})
class HistorialDelUsuarioEnPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AdaptadorRepositorioDeUsuarios usuarios;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void sinPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Usuario registrado() {
        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();
        return usuarios.guardar(Usuario.registrar(id, new CorreoElectronico(id + "@example.pe"),
                new Celular("987654321"), new ContrasenaCifrada("$argon2id$v=19$m=19456,t=2,p=1$prueba"),
                Consentimiento.otorgar(true, ahora, "v1"), ahora));
    }

    private static String estadoSql(DataAccessException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql ? sql.getSQLState() : null;
    }

    @Test
    @DisplayName("El registro y la activacion dejan dos versiones; la segunda con el actor de la sesion")
    void versionesConActor() {
        Usuario ana = registrado();

        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/registro/verificacion");
        peticion.addHeader("X-Ayni-Usuario", ana.id().toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
        usuarios.guardar(ana.activar());

        List<Map<String, Object>> versiones = jdbc.queryForList("""
                SELECT a.revtype::int AS revtype, a.estado, r.actor
                FROM identity.usuario_aud a JOIN identity.revision_auditoria r ON r.id = a.rev
                WHERE a.id = ? ORDER BY a.rev""", ana.id());
        assertThat(versiones).hasSize(2);
        assertThat(versiones.get(0)).containsEntry("revtype", 0).containsEntry("estado", "PENDIENTE_VERIFICACION")
                .containsEntry("actor", null);
        assertThat(versiones.get(1)).containsEntry("revtype", 1).containsEntry("estado", "ACTIVO")
                .containsEntry("actor", ana.id());
    }

    @Test
    @DisplayName("La contrasena no entra en el historial, y el historial no se puede reescribir")
    void sinContrasenaEInalterable() {
        Usuario luis = registrado();

        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = 'identity' AND table_name = 'usuario_aud'", String.class))
                .doesNotContain("contrasena_hash");
        assertThatThrownBy(() -> jdbc.update("UPDATE identity.usuario_aud SET estado = 'BLOQUEADO' WHERE id = ?",
                luis.id()))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM identity.revision_auditoria"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
    }
}
