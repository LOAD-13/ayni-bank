package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Los cinco roles y sus permisos contra PostgreSQL real (AYNI-281): la migracion V13, el trigger
 * que hace cliente a todo usuario nuevo y la lectura de permisos que alimenta el token.
 */
@Testcontainers
class RolesDeUsuarioEnPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static AdaptadorLectorDePermisos lector;

    @BeforeAll
    static void migrar() {
        DriverManagerDataSource datos = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(datos).schemas("identity").defaultSchema("identity").load().migrate();
        jdbc = new JdbcTemplate(datos);
        lector = new AdaptadorLectorDePermisos(jdbc);
    }

    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity.usuario (id, correo, celular, contrasena_hash, estado,"
                + " consentimiento_en, terminos_version) VALUES (?, ?, '987654321', '$argon2id$x', 'ACTIVO',"
                + " now(), 'v1')", id, id + "@example.pe");
        return id;
    }

    private void asignar(UUID usuario, String rol) {
        jdbc.update("INSERT INTO identity.usuario_rol (usuario_id, rol_id)"
                + " SELECT ?, id FROM identity.rol WHERE codigo = ?", usuario, rol);
    }

    @Test
    @DisplayName("Todo usuario nuevo nace CLIENTE y sus permisos son los del cliente")
    void nuevoUsuarioEsCliente() {
        UUID ana = nuevoUsuario();

        assertThat(lector.permisosDe(ana)).containsExactlyInAnyOrder(
                "cuenta:leer", "transferencia:iniciar", "tarjeta:administrar", "tarjeta:revelar_pan");
    }

    @Test
    @DisplayName("El cliente no tiene ningun permiso de personal")
    void clienteSinPermisosDePersonal() {
        assertThat(lector.permisosDe(nuevoUsuario())).doesNotContain(
                "cuenta:leer_todas", "transferencia:aprobar", "cliente:bloquear_aprobar",
                "kyc:revisar", "auditoria:leer");
    }

    @Test
    @DisplayName("Los permisos de dos roles se suman sin repetirse")
    void dosRolesSeSuman() {
        UUID luis = nuevoUsuario();
        asignar(luis, "AUDITOR");

        assertThat(lector.permisosDe(luis)).contains("cuenta:leer", "auditoria:leer", "cuenta:leer_todas")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Segregacion de funciones: ningun rol de personal inicia y aprueba, ni mueve dinero quien cumple o audita")
    void segregacionDeFunciones() {
        assertThat(permisosDelRol("OPERADOR")).contains("cliente:bloquear_iniciar")
                .doesNotContain("cliente:bloquear_aprobar", "transferencia:aprobar", "tarjeta:revelar_pan");
        assertThat(permisosDelRol("SUPERVISOR")).contains("cliente:bloquear_aprobar", "transferencia:aprobar")
                .doesNotContain("cliente:bloquear_iniciar", "transferencia:iniciar");
        assertThat(permisosDelRol("AUDITOR")).contains("auditoria:leer")
                .allSatisfy(p -> assertThat(p).doesNotContain("iniciar", "aprobar", "bloquear", "administrar"));
        assertThat(permisosDelRol("OFICIAL_CUMPLIMIENTO")).contains("kyc:revisar", "kyc:marcar_sospechoso")
                .doesNotContain("transferencia:iniciar", "transferencia:aprobar");
    }

    @Test
    @DisplayName("Un usuario sin ningun rol no tiene permisos")
    void sinRolesSinPermisos() {
        UUID sinRol = nuevoUsuario();
        jdbc.update("DELETE FROM identity.usuario_rol WHERE usuario_id = ?", sinRol);

        assertThat(lector.permisosDe(sinRol)).isEmpty();
    }

    @Test
    @DisplayName("Borrar al usuario borra sus roles")
    void alBorrarSeVanSusRoles() {
        UUID id = nuevoUsuario();
        jdbc.update("DELETE FROM identity.usuario WHERE id = ?", id);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.usuario_rol WHERE usuario_id = ?",
                Integer.class, id)).isZero();
    }

    private java.util.List<String> permisosDelRol(String rol) {
        return jdbc.queryForList("SELECT p.codigo FROM identity.rol r"
                + " JOIN identity.rol_permiso rp ON rp.rol_id = r.id JOIN identity.permiso p ON p.id = rp.permiso_id"
                + " WHERE r.codigo = ?", String.class, rol);
    }
}
