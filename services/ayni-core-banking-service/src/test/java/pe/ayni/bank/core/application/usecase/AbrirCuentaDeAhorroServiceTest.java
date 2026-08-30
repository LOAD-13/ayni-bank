package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.CuentaDuplicadaException;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** Los cuatro escenarios de aceptación de HU-05, sin Spring, sin base y sin RabbitMQ. */
class AbrirCuentaDeAhorroServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final short PRODUCTO_AHORRO_PEN = 1;

    private final UUID titular = UUID.randomUUID();
    private final UUID solicitud = UUID.randomUUID();

    private CuentasFalsas cuentas;
    private EventosFalsos eventos;
    private IdempotenciaFalsa idempotencia;
    private AbrirCuentaDeAhorroService servicio;

    @BeforeEach
    void prepararEscenario() {
        cuentas = new CuentasFalsas();
        eventos = new EventosFalsos();
        idempotencia = new IdempotenciaFalsa();
        servicio = new AbrirCuentaDeAhorroService(cuentas, eventos, idempotencia,
                PRODUCTO_AHORRO_PEN, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("Escenario 1 · Apertura tras aprobación del KYC")
    class Apertura {

        @Test
        @DisplayName("crea la cuenta en soles, ACTIVA y con saldo cero")
        void abreLaCuenta() {
            Cuenta cuenta = servicio.abrirPara(titular, solicitud, Moneda.PEN);

            assertThat(cuenta.moneda()).isEqualTo(Moneda.PEN);
            assertThat(cuenta.estado().name()).isEqualTo("ACTIVA");
            assertThat(cuenta.saldo(List.of())).isEqualTo(Dinero.cero(Moneda.PEN));
            assertThat(cuentas.guardadas).hasSize(1);
        }

        @Test
        @DisplayName("genera un número único y su CCI de 20 dígitos")
        void generaNumeroYCci() {
            Cuenta primera = servicio.abrirPara(titular, solicitud, Moneda.PEN);
            Cuenta segunda = servicio.abrirPara(UUID.randomUUID(), UUID.randomUUID(),
                    Moneda.PEN);

            assertThat(primera.cci().valor()).hasSize(20);
            assertThat(primera.numero()).isNotEqualTo(segunda.numero());
            assertThat(primera.cci()).isNotEqualTo(segunda.cci());
        }

        @Test
        @DisplayName("escribe CuentaAperturada en la bandeja de salida")
        void registraElEvento() {
            Cuenta cuenta = servicio.abrirPara(titular, solicitud, Moneda.PEN);

            assertThat(eventos.registrados).hasSize(1);
            var evento = eventos.registrados.get(0);
            assertThat(evento.tipo()).isEqualTo("CuentaAperturada");
            assertThat(evento.agregadoId()).isEqualTo(cuenta.id());
        }

        @Test
        @DisplayName("la cuenta usa el producto de ahorro en soles del catálogo")
        void usaElProductoConfigurado() {
            assertThat(servicio.abrirPara(titular, solicitud, Moneda.PEN).productoId())
                    .isEqualTo(PRODUCTO_AHORRO_PEN);
        }
    }

    @Nested
    @DisplayName("Escenario 2 · Apertura duplicada")
    class Duplicada {

        @Test
        void rechazaUnaSegundaCuentaEnLaMismaMoneda() {
            servicio.abrirPara(titular, solicitud, Moneda.PEN);

            assertThatThrownBy(() -> servicio.abrirPara(titular, UUID.randomUUID(),
                    Moneda.PEN))
                    .isInstanceOf(CuentaDuplicadaException.class);

            assertThat(cuentas.guardadas).hasSize(1);
        }

        @Test
        @DisplayName("no escribe ningún evento si no llegó a abrir nada")
        void noAnunciaLoQueNoOcurrio() {
            servicio.abrirPara(titular, solicitud, Moneda.PEN);
            eventos.registrados.clear();

            assertThatThrownBy(() -> servicio.abrirPara(titular, UUID.randomUUID(),
                    Moneda.PEN))
                    .isInstanceOf(CuentaDuplicadaException.class);

            assertThat(eventos.registrados).isEmpty();
        }
    }

    @Nested
    @DisplayName("Idempotencia · el mismo evento entregado dos veces")
    class Idempotencia {

        @Test
        @DisplayName("el evento repetido devuelve la cuenta existente, no crea otra")
        void noAbreDosCuentasPorElMismoEvento() {
            // RabbitMQ entrega «al menos una vez». Un corte de red en el momento
            // equivocado hace que el mismo evento llegue dos veces, y sin esto serían dos
            // cuentas para la misma persona.
            Cuenta primera = servicio.abrirPara(titular, solicitud, Moneda.PEN);
            Cuenta repetida = servicio.abrirPara(titular, solicitud, Moneda.PEN);

            assertThat(repetida).isEqualTo(primera);
            assertThat(cuentas.guardadas).hasSize(1);
        }

        @Test
        @DisplayName("tampoco duplica el evento en la bandeja de salida")
        void noAnunciaDosVeces() {
            servicio.abrirPara(titular, solicitud, Moneda.PEN);
            servicio.abrirPara(titular, solicitud, Moneda.PEN);

            assertThat(eventos.registrados).hasSize(1);
        }

        @Test
        @DisplayName("recuerda la operación con la solicitud como clave")
        void recuerdaLaOperacion() {
            Cuenta cuenta = servicio.abrirPara(titular, solicitud, Moneda.PEN);

            assertThat(idempotencia.resultadoDe(solicitud)).contains(cuenta.id());
        }
    }

    // ─── Dobles ────────────────────────────────────────────────────────────

    private static final class CuentasFalsas implements RepositorioDeCuentasPort {
        private final List<Cuenta> guardadas = new ArrayList<>();
        private long correlativo = 1_000_000_000L;

        @Override
        public boolean existeCuentaActiva(UUID usuarioId, Moneda moneda) {
            return buscarActivaDe(usuarioId, moneda).isPresent();
        }

        @Override
        public Optional<Cuenta> buscarActivaDe(UUID usuarioId, Moneda moneda) {
            return guardadas.stream()
                    .filter(c -> c.usuarioId().equals(usuarioId) && c.moneda() == moneda)
                    .findFirst();
        }

        @Override
        public long siguienteCorrelativo() {
            return ++correlativo;
        }

        @Override
        public Cuenta guardar(Cuenta cuenta) {
            guardadas.add(cuenta);
            return cuenta;
        }

        @Override
        public List<Asiento> asientosDe(UUID cuentaId) {
            return List.of();
        }

        @Override
        public Optional<java.math.BigDecimal> treaVigenteDe(short productoId) {
            return Optional.of(new java.math.BigDecimal("4.50"));
        }
    }

    private static final class EventosFalsos implements PublicadorDeEventosPort {
        private final List<EventoRegistrado> registrados = new ArrayList<>();

        @Override
        public void registrar(String agregadoTipo, UUID agregadoId, String tipoDeEvento,
                              Object carga) {
            registrados.add(new EventoRegistrado(agregadoTipo, agregadoId, tipoDeEvento));
        }

        record EventoRegistrado(String agregado, UUID agregadoId, String tipo) {
        }
    }

    private static final class IdempotenciaFalsa implements RegistroDeIdempotenciaPort {
        private final Map<UUID, UUID> hechas = new HashMap<>();

        @Override
        public Optional<UUID> resultadoDe(UUID claveDeIdempotencia) {
            return Optional.ofNullable(hechas.get(claveDeIdempotencia));
        }

        @Override
        public void recordar(UUID claveDeIdempotencia, UUID resultado) {
            hechas.put(claveDeIdempotencia, resultado);
        }
    }
}
