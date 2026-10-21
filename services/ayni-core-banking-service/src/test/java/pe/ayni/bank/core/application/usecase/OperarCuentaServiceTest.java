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
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** Orquestacion de transferencias y depositos: idempotencia, bloqueo, outbox · HU-07. */
class OperarCuentaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final short AHORRO = 1;

    private final Libro libro = new Libro();
    private final Cuentas cuentas = new Cuentas(libro);
    private final List<String> eventos = new ArrayList<>();
    private final List<Object> cargas = new ArrayList<>();
    private final Map<UUID, UUID> claves = new HashMap<>();
    private final List<String> pista = new ArrayList<>();

    private static final String CONFIRMADA = "token-de-confirmacion";
    private final List<String> confirmacionesVistas = new ArrayList<>();

    private final OperarCuentaService servicio = new OperarCuentaService(cuentas, libro,
            (tipo, id, evento, carga) -> {
                eventos.add(evento);
                cargas.add(carga);
            },
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
            new PistaDeAuditoriaPort() {
                @Override
                public void registrar(TipoDeEventoDeOperacion tipo, UUID usuarioId, UUID movimientoId) {
                    pista.add(tipo.name());
                }

                @Override
                public void registrarRechazo(UUID usuarioId, MotivoDeRechazo motivo) {
                    pista.add("RECHAZO:" + motivo.name());
                }

                @Override
                public void registrarAperturaDuplicada(UUID usuarioId) {
                    pista.add("APERTURA_RECHAZADA");
                }

                @Override
                public void registrarAccesoAjeno(UUID usuarioId, UUID movimientoId) {
                    pista.add("ACCESO_AJENO:" + movimientoId);
                }
            },
            (confirmacion, usuario, operacion) -> {
                confirmacionesVistas.add(confirmacion);
                if (!CONFIRMADA.equals(confirmacion)) {
                    throw new ConfirmacionInvalidaException();
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
    @DisplayName("la pista registra depositos, transferencias, repetidas y rechazos")
    void pistaDeAuditoria() {
        UUID clave = UUID.randomUUID();
        servicio.depositarSimulado(ana, soles("100.00"), clave);
        servicio.depositarSimulado(ana, soles("100.00"), clave);
        servicio.transferir(ana, deBeto.numero().valor(), soles("30.00"), "Almuerzo", UUID.randomUUID(), CONFIRMADA);
        assertThatThrownBy(() -> servicio.transferir(ana, deBeto.numero().valor(), soles("999.00"),
                "Demasiado", UUID.randomUUID(), CONFIRMADA)).isInstanceOf(OperacionRechazadaException.class);

        assertThat(pista).containsExactly("DEPOSITO_SIMULADO", "OPERACION_REPETIDA",
                "TRANSFERENCIA_REALIZADA", "RECHAZO:SALDO_INSUFICIENTE");
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
                soles("250.75"), "Cena", UUID.randomUUID(), CONFIRMADA);

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
    @DisplayName("HU-13: el evento de la transferencia lleva a los dos titulares y sus saldos posteriores")
    void eventoParaNotificar() {
        servicio.depositarSimulado(ana, soles("500"), UUID.randomUUID());
        servicio.transferir(ana, deBeto.numero().valor(), soles("120.50"), "Cena", UUID.randomUUID(), CONFIRMADA);

        assertThat(cargas.get(0)).isInstanceOfSatisfying(OperarCuentaService.DepositoSimuladoRegistrado.class, d -> {
            assertThat(d.usuarioId()).isEqualTo(ana);
            assertThat(d.saldo()).isEqualTo("500.00");
            assertThat(d.cuenta()).isEqualTo(deAna.numero().enmascarado());
        });
        assertThat(cargas.get(1)).isInstanceOfSatisfying(OperarCuentaService.TransferenciaRealizada.class, t -> {
            assertThat(t.usuarioOrigenId()).isEqualTo(ana);
            assertThat(t.usuarioDestinoId()).isEqualTo(beto);
            assertThat(t.saldoOrigen()).isEqualTo("379.50");
            assertThat(t.saldoDestino()).isEqualTo("120.50");
            assertThat(t.cuentaOrigen()).isEqualTo(deAna.numero().enmascarado());
            assertThat(t.cuentaDestino()).isEqualTo(deBeto.numero().enmascarado()).doesNotContain(deBeto.numero().valor());
            assertThat(t.registradoEn()).isNotBlank();
        });
    }

    @Test
    @DisplayName("HU-06: el abono de intereses se reconoce por su contrapartida tecnica")
    void comprobanteDeIntereses() {
        UUID id = UUID.randomUUID();
        libro.registrar(Movimiento.capitalizacionDeIntereses(id, libro.intereses, deAna, soles("4.20"), "10/2026",
                AHORA));

        assertThat(servicio.comprobante(ana, id)).get().satisfies(c -> {
            assertThat(c.tipo()).isEqualTo(TipoDeMovimiento.INTERES);
            assertThat(c.cuentaOrigen()).isEqualTo("Intereses Ayni");
            assertThat(c.cuentaDestino()).isEqualTo(deAna.numero().enmascarado());
        });
    }

    @Test
    @DisplayName("HU-07: el comprobante se consulta despues; quien no participa no lo ve")
    void consultarComprobante() {
        servicio.depositarSimulado(ana, soles("500"), UUID.randomUUID());
        Comprobante hecho = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), "Cena", UUID.randomUUID(), CONFIRMADA);

        assertThat(servicio.comprobante(ana, hecho.movimientoId()))
                .get().satisfies(c -> {
                    assertThat(c.movimientoId()).isEqualTo(hecho.movimientoId());
                    assertThat(c.cuentaDestino()).isEqualTo(deBeto.numero().enmascarado());
                    assertThat(c.concepto()).startsWith("Cena");
                });
        assertThat(servicio.comprobante(beto, hecho.movimientoId())).isPresent();
        assertThat(servicio.comprobante(UUID.randomUUID(), hecho.movimientoId())).isEmpty();
        assertThat(servicio.comprobante(ana, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("HU-08 · AYNI-328: pedir el comprobante de otra persona queda como evento de seguridad")
    void accesoAjenoAuditado() {
        UUID movimiento = servicio.depositarSimulado(ana, soles("80.00"), UUID.randomUUID()).movimientoId();
        pista.clear();

        assertThat(servicio.comprobante(beto, movimiento)).isEmpty();

        assertThat(pista).containsExactly("ACCESO_AJENO:" + movimiento);
    }

    @Test
    @DisplayName("HU-08 · AYNI-328: el propio movimiento y un identificador inexistente no son accesos ajenos")
    void sinFalsosPositivos() {
        UUID movimiento = servicio.depositarSimulado(ana, soles("80.00"), UUID.randomUUID()).movimientoId();
        pista.clear();

        assertThat(servicio.comprobante(ana, movimiento)).isPresent();
        assertThat(servicio.comprobante(beto, UUID.randomUUID())).isEmpty();

        assertThat(pista).isEmpty();
    }

    @Test
    @DisplayName("HU-08 · AYNI-328: el titular de una cuenta bloqueada no ve su movimiento, pero no es un acceso ajeno")
    void cuentaBloqueadaNoEsAccesoAjeno() {
        UUID movimiento = servicio.depositarSimulado(ana, soles("80.00"), UUID.randomUUID()).movimientoId();
        libro.porId.put(deAna.id(), deAna.bloquear());
        pista.clear();

        assertThat(servicio.comprobante(ana, movimiento)).isEmpty();

        assertThat(pista).isEmpty();
    }

    @Test
    @DisplayName("repetir con la misma clave devuelve el mismo comprobante y no mueve dinero")
    void idempotente() {
        servicio.depositarSimulado(ana, soles("500"), UUID.randomUUID());
        UUID clave = UUID.randomUUID();

        Comprobante primera = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null, clave, CONFIRMADA);
        Comprobante segunda = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null, clave, CONFIRMADA);

        assertThat(segunda.movimientoId()).isEqualTo(primera.movimientoId());
        assertThat(segunda.tipo()).isEqualTo(TipoDeMovimiento.TRANSFERENCIA);
        assertThat(segunda.cuentaDestino()).isEqualTo(deBeto.numero().enmascarado());
        assertThat(libro.saldoDe(deBeto.id(), Moneda.PEN)).isEqualTo(soles("100.00"));
        assertThat(eventos).hasSize(2);
    }

    @Test
    @DisplayName("HU-07: sin confirmacion con segundo factor no se mueve dinero")
    void sinConfirmacion() {
        servicio.depositarSimulado(ana, soles("500"), UUID.randomUUID());

        assertThatThrownBy(() -> servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null,
                UUID.randomUUID(), "falsa")).isInstanceOf(ConfirmacionInvalidaException.class);
        assertThatThrownBy(() -> servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null,
                UUID.randomUUID(), null)).isInstanceOf(ConfirmacionInvalidaException.class);

        assertThat(libro.saldoDe(deBeto.id(), Moneda.PEN)).isEqualTo(soles("0"));
        assertThat(eventos).containsExactly("DepositoSimuladoRegistrado");
    }

    @Test
    @DisplayName("HU-07: un reintento con la misma clave no vuelve a pedir la confirmacion")
    void reintentoSinReconfirmar() {
        servicio.depositarSimulado(ana, soles("500"), UUID.randomUUID());
        UUID clave = UUID.randomUUID();
        Comprobante primera = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null, clave, CONFIRMADA);

        Comprobante reintento = servicio.transferir(ana, deBeto.numero().valor(), soles("100"), null, clave, "caducada");

        assertThat(reintento.movimientoId()).isEqualTo(primera.movimientoId());
        assertThat(confirmacionesVistas).containsExactly(CONFIRMADA);
    }

    @Test
    @DisplayName("el deposito simulado no pide segundo factor")
    void depositoSinConfirmacion() {
        servicio.depositarSimulado(ana, soles("10"), UUID.randomUUID());
        assertThat(confirmacionesVistas).isEmpty();
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
        servicio.transferir(ana, deBeto.numero().valor(), soles("40"), null, clave, CONFIRMADA);

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
                null, UUID.randomUUID(), CONFIRMADA))
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
            assertThatThrownBy(() -> servicio.transferir(ana, numero, soles("1"), null, UUID.randomUUID(), CONFIRMADA))
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
        final Cuenta intereses = Cuenta.reconstituir(UUID.randomUUID(), UUID.randomUUID(), AHORRO,
                new NumeroDeCuenta("00110000000003"), new Cci("99900101000000000309"), Moneda.PEN,
                EstadoCuenta.ACTIVA, AHORA);

        Libro() {
            porId.put(fondeo.id(), fondeo);
            porId.put(intereses.id(), intereses);
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
        public Cuenta cuentaDeIntereses(Moneda moneda) {
            return intereses;
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
                    .filter(c -> c.usuarioId().equals(usuarioId) && c.moneda() == moneda
                            && c.estado() == EstadoCuenta.ACTIVA)
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
