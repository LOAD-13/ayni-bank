package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cci;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** Orquestacion de transferencias y depositos: idempotencia, bloqueo, outbox · HU-07. */
class OperarCuentaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final short AHORRO = 1;

    private final Libro libro = new Libro();
    private final Cuentas cuentas = new Cuentas(libro);
    private final List<String> eventos = new ArrayList<>();
    private final Map<UUID, UUID> claves = new HashMap<>();

    private final OperarCuentaService servicio = new OperarCuentaService(cuentas, libro,
            (tipo, id, evento, carga) -> eventos.add(evento),
            new RegistroDeIdempotenciaPort() {
                @Override
                public Optional<UUID> resultadoDe(UUID clave) {
                    return Optional.ofNullable(claves.get(clave));
                }

                @Override
                public void recordar(UUID clave, UUID resultado) {
                    claves.put(clave, resultado);
                }
            },
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();
    private final Cuenta deAna = libro.abrir(ana, 1_000_000_020L);
    private final Cuenta deBeto = libro.abrir(beto, 1_000_000_021L);

    private static Dinero soles(String importe) {
        return Dinero.de(importe, Moneda.PEN);
    }

    @Test
    @DisplayName("depositar, transferir y que los saldos cuadren")
    void flujoCompleto() {
        Comprobante deposito = servicio.depositarSimulado(ana, soles("1000.00"), UUID.randomUUID());
        assertThat(deposito.tipo()).isEqualTo(TipoDeMovimiento.DEPOSITO_SIMULADO);
        assertThat(deposito.cuentaOrigen()).isEqualTo("Deposito simulado");
        assertThat(deposito.cuentaDestino()).isEqualTo(deAna.numero().enmascarado());
        assertThat(deposito.saldoDisponible()).isEqualTo(soles("1000.00"));

        Comprobante transferencia = servicio.transferir(ana, deBeto.numero().formateado(),
                soles("250.75"), "Cena", UUID.randomUUID());

        assertThat(transferencia.tipo()).isEqualTo(TipoDeMovimiento.TRANSFERENCIA);
        assertThat(transferencia.cuentaOrigen()).isEqualTo(deAna.numero().enmascarado());
        assertThat(transferencia.cuentaDestino()).isEqualTo(deBeto.numero().enmascarado());
        assertThat(transferencia.saldoDisponible()).isEqualTo(soles("749.25"));
        assertThat(libro.saldoDe(deBeto.id(), Moneda.PEN)).isEqualTo(soles("250.75"));
        // El fondeo refleja exactamente el dinero que entro.
        assertThat(libro.saldoDe(libro.fondeo.id(), Moneda.PEN)).isEqualTo(soles("-1000.00"));
        assertThat(eventos).containsExactly("DepositoSimuladoRegistrado", "TransferenciaRealizada");
        assertThat(libro.bloqueos).allSatisfy(ids ->
                assertThat(ids).isSortedAccordingTo(Comparator.naturalOrder()));
    }

    @Test
    @DisplayName("repetir con la misma clave devuelve el mismo comprobante y no mueve dinero")
    void idempotente() {
        servicio.depositarSimulado(ana, soles("500"), UUID.randomUUID());
        UUID clave = UUID.randomUUID();

        Comprobante primera = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null, clave);
        Comprobante segunda = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null, clave);

        assertThat(segunda.movimientoId()).isEqualTo(primera.movimientoId());
        assertThat(segunda.tipo()).isEqualTo(TipoDeMovimiento.TRANSFERENCIA);
        assertThat(segunda.cuentaDestino()).isEqualTo(deBeto.numero().enmascarado());
        assertThat(libro.saldoDe(deBeto.id(), Moneda.PEN)).isEqualTo(soles("100.00"));
        assertThat(eventos).hasSize(2);
    }

    @Test
    @DisplayName("un deposito repetido tampoco se duplica")
    void depositoIdempotente() {
        UUID clave = UUID.randomUUID();
        servicio.depositarSimulado(ana, soles("100"), clave);
        Comprobante otra = servicio.depositarSimulado(ana, soles("100"), clave);

        assertThat(otra.tipo()).isEqualTo(TipoDeMovimiento.DEPOSITO_SIMULADO);
        assertThat(libro.saldoDe(deAna.id(), Moneda.PEN)).isEqualTo(soles("100.00"));
    }

    @Test
    @DisplayName("del lado del beneficiario, el comprobante muestra al ordenante enmascarado")
    void comprobanteDelBeneficiario() {
        servicio.depositarSimulado(ana, soles("100"), UUID.randomUUID());
        UUID clave = UUID.randomUUID();
        servicio.transferir(ana, deBeto.numero().valor(), soles("40"), null, clave);

        // Beto usa la misma clave: la operacion le toca (es el beneficiario), asi que la ve.
        Comprobante visto = servicio.depositarSimulado(beto, soles("1"), clave);
        assertThat(visto.cuentaOrigen()).isEqualTo(deAna.numero().enmascarado());
        assertThat(visto.cuentaDestino()).isEqualTo(deBeto.numero().enmascarado());
    }

    @Test
    @DisplayName("una clave usada por otro titular no sirve para leer su operacion")
    void claveAjena() {
        servicio.depositarSimulado(ana, soles("100"), UUID.randomUUID());
        UUID clave = UUID.randomUUID();
        servicio.depositarSimulado(ana, soles("10"), clave);

        assertThatThrownBy(() -> servicio.depositarSimulado(beto, soles("10"), clave))
                .isInstanceOf(ClaveDeIdempotenciaReutilizadaException.class);
    }

    @Test
    @DisplayName("sin saldo suficiente no se mueve nada")
    void sinSaldo() {
        assertThatThrownBy(() -> servicio.transferir(ana, deBeto.numero().valor(), soles("1"),
                null, UUID.randomUUID()))
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(MotivoDeRechazo.SALDO_INSUFICIENTE);
        assertThat(eventos).isEmpty();
        assertThat(claves).isEmpty();
    }

    @Test
    @DisplayName("un destino que no existe, mal escrito o nulo se rechaza igual")
    void destinoInvalido() {
        for (String numero : new String[] {"00111999999999", "no-es-un-numero", null}) {
            assertThatThrownBy(() -> servicio.transferir(ana, numero, soles("1"), null, UUID.randomUUID()))
                    .isInstanceOf(OperacionRechazadaException.class)
                    .extracting(e -> ((OperacionRechazadaException) e).motivo())
                    .isEqualTo(MotivoDeRechazo.CUENTA_DESTINO_INEXISTENTE);
        }
    }

    @Test
    @DisplayName("quien no tiene cuenta no puede operar")
    void sinCuenta() {
        UUID nadie = UUID.randomUUID();
        assertThatThrownBy(() -> servicio.depositarSimulado(nadie, soles("1"), UUID.randomUUID()))
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(MotivoDeRechazo.SIN_CUENTA);
    }

    // ── Dobles en memoria ──────────────────────────────────────────────────

    static class Libro implements LibroMayorPort {

        final Map<UUID, Cuenta> porId = new HashMap<>();
        final List<Asiento> asientos = new ArrayList<>();
        final List<List<UUID>> bloqueos = new ArrayList<>();
        final Cuenta fondeo = Cuenta.reconstituir(UUID.randomUUID(), UUID.randomUUID(), AHORRO,
                new NumeroDeCuenta("00110000000001"), new Cci("99900101000000000103"), Moneda.PEN,
                EstadoCuenta.ACTIVA, AHORA);

        Libro() {
            porId.put(fondeo.id(), fondeo);
        }

        Cuenta abrir(UUID titular, long correlativo) {
            Cuenta cuenta = Cuenta.abrir(UUID.randomUUID(), titular, AHORRO,
                    NumeroDeCuenta.de(Moneda.PEN, correlativo), Moneda.PEN, AHORA);
            porId.put(cuenta.id(), cuenta);
            return cuenta;
        }

        @Override
        public Optional<Cuenta> buscarPorNumero(NumeroDeCuenta numero) {
            return porId.values().stream()
                    .filter(c -> c.numero().equals(numero) && !c.equals(fondeo)).findFirst();
        }

        @Override
        public Optional<Cuenta> buscarPorId(UUID cuentaId) {
            return Optional.ofNullable(porId.get(cuentaId));
        }

        @Override
        public Cuenta cuentaDeFondeo(Moneda moneda) {
            return fondeo;
        }

        @Override
        public void bloquear(List<UUID> cuentaIds) {
            bloqueos.add(cuentaIds);
        }

        @Override
        public Dinero saldoDe(UUID cuentaId, Moneda moneda) {
            Dinero total = Dinero.cero(moneda);
            for (Asiento a : asientos) {
                if (a.cuentaId().equals(cuentaId)) {
                    total = total.mas(a.efecto());
                }
            }
            return total;
        }

        @Override
        public void registrar(Movimiento movimiento) {
            asientos.addAll(movimiento.asientos());
        }

        @Override
        public List<Asiento> ultimosAsientosDe(UUID cuentaId, int limite) {
            return asientos.stream().filter(a -> a.cuentaId().equals(cuentaId)).limit(limite).toList();
        }

        @Override
        public List<Asiento> asientosDelMovimiento(UUID movimientoId) {
            return asientos.stream().filter(a -> a.movimientoId().equals(movimientoId)).toList();
        }
    }

    static class Cuentas implements RepositorioDeCuentasPort {

        private final Libro libro;

        Cuentas(Libro libro) {
            this.libro = libro;
        }

        @Override
        public boolean existeCuentaActiva(UUID usuarioId, Moneda moneda) {
            return buscarActivaDe(usuarioId, moneda).isPresent();
        }

        @Override
        public Optional<Cuenta> buscarActivaDe(UUID usuarioId, Moneda moneda) {
            return libro.porId.values().stream()
                    .filter(c -> c.usuarioId().equals(usuarioId) && c.moneda() == moneda)
                    .findFirst();
        }

        @Override
        public long siguienteCorrelativo() {
            return 0;
        }

        @Override
        public Cuenta guardar(Cuenta cuenta) {
            return cuenta;
        }

        @Override
        public List<Asiento> asientosDe(UUID cuentaId) {
            return List.of();
        }

        @Override
        public Optional<BigDecimal> treaVigenteDe(short productoId) {
            return Optional.empty();
        }
    }
}
