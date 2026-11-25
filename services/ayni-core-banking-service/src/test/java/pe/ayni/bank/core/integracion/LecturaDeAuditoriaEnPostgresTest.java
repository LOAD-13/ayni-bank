package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.domain.model.FiltroDeAuditoria;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLecturaDeAuditoria;

/** La lectura de la pista contra PostgreSQL real: filtros, orden y paginas (AYNI-284). */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdaptadorLecturaDeAuditoria.class)
class LecturaDeAuditoriaEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AdaptadorLecturaDeAuditoria lectura;
    @Autowired
    private JdbcTemplate jdbc;

    private void evento(UUID usuario, String tipo, String instante, String ip, String recurso) {
        jdbc.update("INSERT INTO core.evento_auditoria (tipo, usuario_id, movimiento_id, ocurrido_en, ip, recurso,"
                        + " resultado) VALUES (?, ?, ?, ?::timestamptz, ?, ?, 'EXITO')",
                tipo, usuario, UUID.randomUUID(), instante, ip, recurso);
    }

    @Test
    @DisplayName("Filtra por usuario, y cada fila trae actor, accion, recurso, IP, resultado y momento")
    void porUsuario() {
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        evento(ana, "TRANSFERENCIA_REALIZADA", "2026-11-20T15:00:00Z", "203.0.113.5", "POST /api/v1/transferencias");
        evento(beto, "DEPOSITO_SIMULADO", "2026-11-20T16:00:00Z", "203.0.113.6", "POST /depositos");

        PaginaDeAuditoria pagina = lectura.buscar(new FiltroDeAuditoria(ana, null, null, null), 0, 20);

        assertThat(pagina.total()).isOne();
        assertThat(pagina.eventos()).singleElement().satisfies(e -> {
            assertThat(e.usuarioId()).isEqualTo(ana);
            assertThat(e.tipo()).isEqualTo("TRANSFERENCIA_REALIZADA");
            assertThat(e.recurso()).isEqualTo("POST /api/v1/transferencias");
            assertThat(e.ip()).isEqualTo("203.0.113.5");
            assertThat(e.resultado()).isEqualTo("EXITO");
            assertThat(e.ocurridoEn()).isEqualTo(Instant.parse("2026-11-20T15:00:00Z"));
        });
    }

    @Test
    @DisplayName("Filtra por tipo y por rango [desde, hasta), y viene del mas reciente al mas antiguo")
    void porTipoYRango() {
        UUID usuario = UUID.randomUUID();
        evento(usuario, "PAN_REVELADO", "2026-11-01T10:00:00Z", null, null);
        evento(usuario, "PAN_REVELADO", "2026-11-10T10:00:00Z", null, null);
        evento(usuario, "PAN_REVELADO", "2026-11-20T10:00:00Z", null, null);
        evento(usuario, "DEPOSITO_SIMULADO", "2026-11-10T11:00:00Z", null, null);

        PaginaDeAuditoria pagina = lectura.buscar(new FiltroDeAuditoria(usuario, "PAN_REVELADO",
                Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-20T10:00:00Z")), 0, 20);

        assertThat(pagina.eventos()).extracting(e -> e.ocurridoEn().toString())
                .containsExactly("2026-11-10T10:00:00Z", "2026-11-01T10:00:00Z");
    }

    @Test
    @DisplayName("Pagina de a N con el total completo, sin repetir ni saltarse filas")
    void paginacion() {
        UUID usuario = UUID.randomUUID();
        for (int dia = 1; dia <= 5; dia++) {
            evento(usuario, "DEPOSITO_SIMULADO", "2026-11-0" + dia + "T12:00:00Z", null, null);
        }
        FiltroDeAuditoria filtro = new FiltroDeAuditoria(usuario, null, null, null);

        PaginaDeAuditoria primera = lectura.buscar(filtro, 0, 2);
        PaginaDeAuditoria segunda = lectura.buscar(filtro, 1, 2);
        PaginaDeAuditoria tercera = lectura.buscar(filtro, 2, 2);

        assertThat(primera.total()).isEqualTo(5);
        assertThat(primera.eventos()).hasSize(2);
        assertThat(tercera.eventos()).hasSize(1);
        assertThat(Stream.of(primera, segunda, tercera).flatMap(p -> p.eventos().stream())
                .map(e -> e.ocurridoEn().toString()))
                .containsExactly("2026-11-05T12:00:00Z", "2026-11-04T12:00:00Z", "2026-11-03T12:00:00Z",
                        "2026-11-02T12:00:00Z", "2026-11-01T12:00:00Z");
    }

    @Test
    @DisplayName("Un valor con comillas es un valor, no SQL: no encuentra nada y no rompe la tabla")
    void sinInyeccion() {
        PaginaDeAuditoria pagina = lectura.buscar(
                new FiltroDeAuditoria(null, "x' OR '1'='1", null, null), 0, 20);

        assertThat(pagina.total()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.evento_auditoria", Integer.class)).isNotNull();
    }
}
