package pe.ayni.bank.core.aceptacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import pe.ayni.bank.core.application.usecase.ConsultarTarjetaService;
import pe.ayni.bank.core.application.usecase.ControlarTarjetaService;
import pe.ayni.bank.core.application.usecase.EmitirTarjetaService;
import pe.ayni.bank.core.application.usecase.OperarCuentaService;
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.port.out.VerificadorDeConfirmacionPort;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTarjeta;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada;
import pe.ayni.bank.core.infrastructure.interbancaria.ConciliadorInterbancario;
import pe.ayni.bank.core.infrastructure.interbancaria.ResolucionInterbancariaService;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService;
import pe.ayni.bank.core.infrastructure.out.crypto.CifradorDePan;
import pe.ayni.bank.core.infrastructure.out.messaging.PublicadorDeOutbox;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIntereses;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeTarjetas;
import pe.ayni.bank.core.infrastructure.out.persistence.BandejaDeSalida;

/**
 * El contexto de Spring de los escenarios: los casos de uso reales, sus adaptadores y un
 * PostgreSQL de Testcontainers que Spring arranca y apaga como un bean mas.
 *
 * <p>Sin transaccion de prueba: cada cierre confirma la suya, como en produccion. Los pasos
 * limpian lo que dejo el escenario anterior.
 *
 * <p>RabbitMQ es lo unico simulado: los escenarios de HU-05 necesitan que el broker falle y
 * vuelva a voluntad, y lo que se comprueba es la bandeja de salida, que si es la real.
 */
@CucumberContextConfiguration
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CierreDiarioService.class, ConsultarRendimientoService.class, AbrirCuentaDeAhorroService.class,
        OperarCuentaService.class, EscuchadorDeSolicitudAprobada.class, PublicadorDeOutbox.class,
        BandejaDeSalida.class, ConfiguracionDeMensajeria.class,
        AdaptadorRegistroDeIntereses.class, AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class,
        AdaptadorPublicadorDeEventos.class, AdaptadorPistaDeAuditoria.class, AdaptadorRegistroDeIdempotencia.class,
        ObjectMapper.class, TransferenciaInterbancariaService.class, ResolucionInterbancariaService.class,
        ConciliadorInterbancario.class, EmitirTarjetaService.class, ConsultarTarjetaService.class,
        ControlarTarjetaService.class,
        AdaptadorRepositorioDeTarjetas.class, CifradorDePan.class, ConfiguracionDeTarjeta.class,
        ContextoDeAceptacion.Infraestructura.class})
public class ContextoDeAceptacion {

    /** El token de confirmacion que el verificador de los escenarios da por bueno. */
    public static final String CONFIRMACION_VALIDA = "confirmada-con-segundo-factor";

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

        /** El broker de los escenarios: los pasos lo hacen fallar o responder. */
        @Bean
        RabbitTemplate rabbit() {
            return Mockito.mock(RabbitTemplate.class);
        }

        /**
         * El segundo factor de los escenarios: solo {@link #CONFIRMACION_VALIDA} confirma. Como
         * el verificador real, se niega con {@link ConfirmacionInvalidaException}.
         */
        @Bean
        VerificadorDeConfirmacionPort confirmaciones() {
            return (confirmacion, usuarioId, operacion) -> {
                if (!CONFIRMACION_VALIDA.equals(confirmacion)) {
                    throw new ConfirmacionInvalidaException();
                }
            };
        }

        /** La camara de compensacion de HU-11: los pasos deciden que responde. */
        @Bean
        CamaraDePrueba camara() {
            return new CamaraDePrueba();
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
