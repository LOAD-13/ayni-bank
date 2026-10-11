package pe.ayni.bank.notification.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.ayni.bank.notification.application.usecase.DespacharNotificacionesService;
import pe.ayni.bank.notification.application.usecase.RegistrarAvisosService;
import pe.ayni.bank.notification.application.usecase.RegistrarTitularService;
import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;
import pe.ayni.bank.notification.domain.model.MensajeRedactado;
import pe.ayni.bank.notification.domain.model.PoliticaDeReintentos;
import pe.ayni.bank.notification.domain.model.ResultadoDelDespacho;
import pe.ayni.bank.notification.domain.model.Titular;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;
import pe.ayni.bank.notification.domain.port.out.EnviadorDeCorreoPort;
import pe.ayni.bank.notification.infrastructure.out.persistence.AdaptadorCatalogoDePlantillas;
import pe.ayni.bank.notification.infrastructure.out.persistence.AdaptadorRepositorioDeNotificaciones;
import pe.ayni.bank.notification.infrastructure.out.persistence.AdaptadorRepositorioDeTitulares;

/**
 * HU-13 contra un PostgreSQL de verdad, con las migraciones de Flyway aplicadas.
 *
 * <p>Lo que aqui se prueba no lo puede probar un doble en memoria: que la restriccion UNIQUE
 * y el ON CONFLICT impiden el segundo aviso aunque el mismo evento llegue a la vez por dos
 * hilos, que el jsonb se lee y se escribe, y que las plantillas de V2 tienen todos los
 * marcadores que el traductor rellena.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({RegistrarAvisosService.class, RegistrarTitularService.class, DespacharNotificacionesService.class,
        AdaptadorRepositorioDeNotificaciones.class, AdaptadorRepositorioDeTitulares.class,
        AdaptadorCatalogoDePlantillas.class, ObjectMapper.class, NotificacionesEnPostgresTest.Dobles.class})
class NotificacionesEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    static final List<String> ENVIADOS = new ArrayList<>();
    static volatile boolean proveedorCaido;

    @TestConfiguration
    static class Dobles {
        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }

        @Bean
        PoliticaDeReintentos politica() {
            return new PoliticaDeReintentos(Duration.ofSeconds(30), 2);
        }

        @Bean
        EnviadorDeCorreoPort enviador() {
            return (Titular destinatario, MensajeRedactado mensaje) -> {
                if (proveedorCaido) {
                    throw new IllegalStateException("SES no responde para " + destinatario.correo());
                }
                ENVIADOS.add(destinatario.correo() + " | " + mensaje.asunto() + " | " + mensaje.texto());
            };
        }
    }

    @Autowired
    RegistrarAvisosService avisos;
    @Autowired
    RegistrarTitularService titulares;
    @Autowired
    DespacharNotificacionesService despachador;
    @Autowired
    JdbcTemplate jdbc;

    private final UUID ana = UUID.randomUUID();

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM notification.intento_envio");
        jdbc.update("DELETE FROM notification.notificacion");
        jdbc.update("DELETE FROM notification.titular");
        ENVIADOS.clear();
        proveedorCaido = false;
    }

    private AvisoDeMovimiento aviso(UUID evento, TipoDeAviso tipo) {
        return new AvisoDeMovimiento(evento, ana, tipo, Map.of("importe", "1,234.50", "moneda", "PEN",
                "cuenta", "****4321", "contraparte", "****8765", "saldo", "765.50",
                "fecha", "11/10/2026 a las 10:04", "operacion", "A1B2C3D4", "periodo", "octubre de 2026"));
    }

    @Test
    @DisplayName("el titular se inserta y despues se actualiza con el ultimo evento")
    void upsertDelTitular() {
        titulares.registrar(new Titular(ana, "ana@example.pe", "Ana"));
        titulares.registrar(new Titular(ana, "ana.nueva@example.pe", "Ana Maria"));

        assertThat(jdbc.queryForMap("SELECT correo, nombre_pila FROM notification.titular WHERE usuario_id = ?", ana))
                .containsEntry("correo", "ana.nueva@example.pe")
                .containsEntry("nombre_pila", "Ana Maria");
    }

    @Test
    @DisplayName("escenarios 1 y 2: el mismo evento entregado a la vez por 8 hilos produce un solo correo")
    void unSoloCorreoConEntregasSimultaneas() throws Exception {
        titulares.registrar(new Titular(ana, "ana@example.pe", "Ana"));
        UUID evento = UUID.randomUUID();
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        List<Callable<Integer>> entregas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            entregas.add(() -> avisos.registrar(List.of(aviso(evento, TipoDeAviso.TRANSFERENCIA_ENVIADA))));
        }
        int nuevos = 0;
        for (Future<Integer> f : hilos.invokeAll(entregas)) {
            nuevos += f.get();
        }
        hilos.shutdown();

        ResultadoDelDespacho r = despachador.despacharPendientes();

        assertThat(nuevos).isEqualTo(1);
        assertThat(r).isEqualTo(new ResultadoDelDespacho(1, 0, 0));
        assertThat(ENVIADOS).singleElement().asString()
                .startsWith("ana@example.pe | Transferencia enviada | ")
                .contains("PEN 1,234.50", "11/10/2026 a las 10:04", "saldo disponible es PEN 765.50", "A1B2C3D4")
                .doesNotContain("{{");
        assertThat(jdbc.queryForObject("SELECT estado FROM notification.notificacion", String.class))
                .isEqualTo("ENVIADA");
        assertThat(jdbc.queryForObject("SELECT resultado FROM notification.intento_envio", String.class))
                .isEqualTo("ENVIADO");

        // Una segunda pasada no reenvia nada.
        assertThat(despachador.despacharPendientes()).isEqualTo(new ResultadoDelDespacho(0, 0, 0));
    }

    @Test
    @DisplayName("las plantillas de V2 y V3 se rellenan con los datos del traductor")
    void plantillasCompletas() {
        titulares.registrar(new Titular(ana, "ana@example.pe", "Ana"));
        avisos.registrar(List.of(aviso(UUID.randomUUID(), TipoDeAviso.TRANSFERENCIA_ENVIADA),
                aviso(UUID.randomUUID(), TipoDeAviso.TRANSFERENCIA_RECIBIDA),
                aviso(UUID.randomUUID(), TipoDeAviso.DEPOSITO_RECIBIDO),
                aviso(UUID.randomUUID(), TipoDeAviso.INTERES_CAPITALIZADO)));

        assertThat(despachador.despacharPendientes().enviadas()).isEqualTo(4);
        assertThat(ENVIADOS).anyMatch(c -> c.contains("Tus intereses de octubre de 2026"));
        assertThat(ENVIADOS).hasSize(4).allSatisfy(c -> assertThat(c).contains("765.50").doesNotContain("{{"));
    }

    @Test
    @DisplayName("si el proveedor falla se reprograma con espera y, agotado el maximo, queda FALLIDA sin el correo en el detalle")
    void reintentosRegistrados() {
        titulares.registrar(new Titular(ana, "ana@example.pe", "Ana"));
        avisos.registrar(List.of(aviso(UUID.randomUUID(), TipoDeAviso.DEPOSITO_RECIBIDO)));
        proveedorCaido = true;

        assertThat(despachador.despacharPendientes()).isEqualTo(new ResultadoDelDespacho(0, 1, 0));
        assertThat(jdbc.queryForObject("SELECT proximo_intento_en FROM notification.notificacion", Instant.class))
                .isEqualTo(AHORA.plusSeconds(30));
        // Con el reloj fijo, el turno aun no llega: no se reintenta antes de tiempo.
        assertThat(despachador.despacharPendientes()).isEqualTo(new ResultadoDelDespacho(0, 0, 0));

        jdbc.update("UPDATE notification.notificacion SET proximo_intento_en = ?", java.sql.Timestamp.from(AHORA));
        assertThat(despachador.despacharPendientes()).isEqualTo(new ResultadoDelDespacho(0, 0, 1));

        assertThat(jdbc.queryForList("SELECT detalle FROM notification.intento_envio ORDER BY numero", String.class))
                .hasSize(2)
                .allSatisfy(d -> assertThat(d).contains("SES no responde").doesNotContain("ana@example.pe"));
        assertThat(jdbc.queryForObject("SELECT estado FROM notification.notificacion", String.class))
                .isEqualTo("FALLIDA");
    }

    @Test
    @DisplayName("un aviso cuyo titular aun no llego espera y sale cuando llega el evento de identity")
    void titularQueLlegaDespues() {
        avisos.registrar(List.of(aviso(UUID.randomUUID(), TipoDeAviso.DEPOSITO_RECIBIDO)));

        assertThat(despachador.despacharPendientes().reprogramadas()).isEqualTo(1);

        titulares.registrar(new Titular(ana, "ana@example.pe", "Ana"));
        jdbc.update("UPDATE notification.notificacion SET proximo_intento_en = ?", java.sql.Timestamp.from(AHORA));
        assertThat(despachador.despacharPendientes().enviadas()).isEqualTo(1);
    }
}
