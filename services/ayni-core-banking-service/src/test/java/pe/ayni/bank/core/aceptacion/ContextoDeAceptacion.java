package pe.ayni.bank.core.aceptacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cucumber.spring.CucumberContextConfiguration;
import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.CierreDiarioService;
import pe.ayni.bank.core.application.usecase.ConsultarRendimientoService;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIntereses;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;

/**
 * El contexto de Spring de los escenarios: los casos de uso reales, sus adaptadores y un
 * PostgreSQL de Testcontainers que Spring arranca y apaga como un bean mas.
 *
 * <p>Sin transaccion de prueba: cada cierre confirma la suya, como en produccion. Los pasos
 * limpian lo que dejo el escenario anterior.
 */
@CucumberContextConfiguration
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CierreDiarioService.class, ConsultarRendimientoService.class, AbrirCuentaDeAhorroService.class,
        AdaptadorRegistroDeIntereses.class, AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class,
        AdaptadorPublicadorDeEventos.class, AdaptadorPistaDeAuditoria.class, AdaptadorRegistroDeIdempotencia.class,
        ObjectMapper.class, ContextoDeAceptacion.Infraestructura.class})
public class ContextoDeAceptacion {

    @TestConfiguration(proxyBeanMethods = false)
    static class Infraestructura {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }

        @Bean
        RelojDePrueba reloj() {
            return new RelojDePrueba();
        }
    }

    /** Un reloj que los pasos mueven a voluntad: abrir la cuenta un dia y consultar otro. */
    public static class RelojDePrueba extends Clock {

        private Instant ahora = Instant.parse("2026-10-01T12:00:00Z");

        public void fijar(Instant instante) {
            ahora = instante;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return Clock.fixed(ahora, zona);
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
