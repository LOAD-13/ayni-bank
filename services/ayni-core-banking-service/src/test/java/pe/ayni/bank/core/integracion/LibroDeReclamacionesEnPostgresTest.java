package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Bien;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Constancia;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Hoja;
import pe.ayni.bank.core.infrastructure.reclamaciones.LibroDeReclamaciones.Tipo;

/** La migracion V15 contra PostgreSQL real: correlativo sin huecos por registro y plazo guardado. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LibroDeReclamaciones.class, LibroDeReclamacionesEnPostgresTest.Reloj.class})
class LibroDeReclamacionesEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            // Jueves 19-nov-2026 a las 15:00 en Lima.
            return Clock.fixed(Instant.parse("2026-11-19T20:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private LibroDeReclamaciones libro;
    @Autowired
    private JdbcTemplate jdbc;

    private static Hoja hoja(Tipo tipo) {
        return new Hoja(tipo, "  Ana Quispe ", "DNI", "45678912", "ana.quispe@ejemplo.pe", null, Bien.SERVICIO,
                "Transferencia interbancaria", new BigDecimal("150.50"),
                "La transferencia figura como enviada pero el otro banco no la recibio.", "Que me devuelvan el importe.");
    }

    @Test
    @DisplayName("Dos hojas seguidas llevan numeros consecutivos y el plazo de 15 dias habiles")
    void registra() {
        Constancia primera = libro.registrar(hoja(Tipo.RECLAMO));
        Constancia segunda = libro.registrar(hoja(Tipo.QUEJA));

        long n1 = Long.parseLong(primera.codigo().substring(8));
        assertThat(primera.codigo()).startsWith("LR-2026-");
        assertThat(segunda.codigo()).isEqualTo("LR-2026-%06d".formatted(n1 + 1));
        assertThat(primera.responderAntesDe()).isEqualTo(LocalDate.of(2026, 12, 10));
        assertThat(jdbc.queryForObject("SELECT nombre FROM core.reclamacion WHERE numero = ?", String.class, n1))
                .isEqualTo("Ana Quispe");
        assertThat(jdbc.queryForObject("SELECT monto_reclamado FROM core.reclamacion WHERE numero = ?",
                BigDecimal.class, n1)).isEqualByComparingTo("150.50");
    }
}
