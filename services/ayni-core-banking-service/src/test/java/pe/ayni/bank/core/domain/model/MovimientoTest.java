package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reglas de la transferencia y del deposito simulado, y la partida doble · HU-07. */
class MovimientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final short AHORRO = 1;

    private final Cuenta origen = cuenta(1_000_000_010L);
    private final Cuenta destino = cuenta(1_000_000_011L);
    private final Cuenta fondeo = Cuenta.reconstituir(UUID.randomUUID(), UUID.randomUUID(), AHORRO,
            new NumeroDeCuenta("00110000000001"), new Cci("99900101000000000103"), Moneda.PEN,
            EstadoCuenta.ACTIVA, AHORA);

    private static Cuenta cuenta(long correlativo) {
        return Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), AHORRO,
                NumeroDeCuenta.de(Moneda.PEN, correlativo), Moneda.PEN, AHORA);
    }

    private static Dinero soles(String importe) {
        return Dinero.de(importe, Moneda.PEN);
    }

    private Movimiento transferir(Dinero saldo, Dinero importe) {
        return Movimiento.transferencia(UUID.randomUUID(), origen, saldo, destino, importe,
                "Alquiler", AHORA);
    }

    private static void rechazada(Runnable operacion, MotivoDeRechazo motivo) {
        assertThatThrownBy(operacion::run)
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(motivo);
    }

    @Test
    @DisplayName("una transferencia carga al ordenante, abona al beneficiario y suma cero")
    void transferenciaPorPartidaDoble() {
        Movimiento movimiento = transferir(soles("500.00"), soles("120.50"));

        assertThat(movimiento.tipo()).isEqualTo(TipoDeMovimiento.TRANSFERENCIA);
        assertThat(movimiento.cargo().cuentaId()).isEqualTo(origen.id());
        assertThat(movimiento.abono().cuentaId()).isEqualTo(destino.id());
        assertThat(movimiento.importe()).isEqualTo(soles("120.50"));
        assertThat(movimiento.cargo().efecto().mas(movimiento.abono().efecto()).esCero()).isTrue();
        assertThat(movimiento.asientos()).hasSize(2);
        assertThat(movimiento.cargo().concepto()).startsWith("Alquiler").contains(destino.numero().enmascarado());
        assertThat(movimiento.abono().concepto()).contains(origen.numero().enmascarado());
    }

    @Test
    @DisplayName("se puede transferir exactamente todo el saldo, ni un centimo mas")
    void elSaldoJusto() {
        assertThat(transferir(soles("100.00"), soles("100.00"))).isNotNull();
        rechazada(() -> transferir(soles("100.00"), soles("100.01")), MotivoDeRechazo.SALDO_INSUFICIENTE);
    }

    @Test
    @DisplayName("los limites: minimo S/ 1.00 y maximo S/ 5,000.00 por transferencia")
    void limitesDeTransferencia() {
        rechazada(() -> transferir(soles("9000"), soles("0.99")), MotivoDeRechazo.IMPORTE_FUERA_DE_LIMITE);
        assertThat(transferir(soles("9000"), soles("1.00"))).isNotNull();
        assertThat(transferir(soles("9000"), soles("5000.00"))).isNotNull();
        rechazada(() -> transferir(soles("9000"), soles("5000.01")), MotivoDeRechazo.IMPORTE_FUERA_DE_LIMITE);
    }

    @Test
    @DisplayName("no se puede transferir a la misma cuenta")
    void mismaCuenta() {
        rechazada(() -> Movimiento.transferencia(UUID.randomUUID(), origen, soles("100"), origen,
                soles("10"), null, AHORA), MotivoDeRechazo.MISMA_CUENTA);
    }

    @Test
    @DisplayName("una cuenta bloqueada no puede enviar ni recibir")
    void cuentaBloqueada() {
        Cuenta bloqueada = destino.bloquear();
        rechazada(() -> Movimiento.transferencia(UUID.randomUUID(), origen, soles("100"), bloqueada,
                soles("10"), null, AHORA), MotivoDeRechazo.CUENTA_NO_OPERATIVA);
        rechazada(() -> Movimiento.transferencia(UUID.randomUUID(), bloqueada, soles("100"), origen,
                soles("10"), null, AHORA), MotivoDeRechazo.CUENTA_NO_OPERATIVA);
    }

    @Test
    @DisplayName("no hay conversion implicita entre monedas")
    void monedasDistintas() {
        Cuenta enDolares = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 2,
                NumeroDeCuenta.de(Moneda.USD, 1_000_000_012L), Moneda.USD, AHORA);
        rechazada(() -> Movimiento.transferencia(UUID.randomUUID(), origen, soles("100"), enDolares,
                soles("10"), null, AHORA), MotivoDeRechazo.MONEDA_DISTINTA);
        rechazada(() -> Movimiento.transferencia(UUID.randomUUID(), origen, soles("100"), destino,
                Dinero.de("10", Moneda.USD), null, AHORA), MotivoDeRechazo.MONEDA_DISTINTA);
    }

    @Test
    @DisplayName("sin concepto, la transferencia se describe como tal")
    void conceptoPorDefecto() {
        Movimiento movimiento = Movimiento.transferencia(UUID.randomUUID(), origen, soles("100"),
                destino, soles("10"), "  ", AHORA);
        assertThat(movimiento.cargo().concepto()).startsWith("Transferencia");
    }

    @Test
    @DisplayName("el deposito simulado carga a la cuenta de fondeo y abona al cliente")
    void depositoSimulado() {
        Movimiento movimiento = Movimiento.depositoSimulado(UUID.randomUUID(), fondeo, destino,
                soles("300.00"), AHORA);

        assertThat(movimiento.tipo()).isEqualTo(TipoDeMovimiento.DEPOSITO_SIMULADO);
        assertThat(movimiento.cargo().cuentaId()).isEqualTo(fondeo.id());
        assertThat(movimiento.abono().cuentaId()).isEqualTo(destino.id());
        assertThat(movimiento.abono().concepto()).isEqualTo("Deposito simulado");
    }

    @Test
    @DisplayName("el deposito simulado tiene tope de S/ 2,000.00 y respeta la moneda")
    void limitesDelDeposito() {
        rechazada(() -> Movimiento.depositoSimulado(UUID.randomUUID(), fondeo, destino,
                soles("2000.01"), AHORA), MotivoDeRechazo.IMPORTE_FUERA_DE_LIMITE);
        rechazada(() -> Movimiento.depositoSimulado(UUID.randomUUID(), fondeo, destino,
                Dinero.de("10", Moneda.USD), AHORA), MotivoDeRechazo.MONEDA_DISTINTA);
        rechazada(() -> Movimiento.depositoSimulado(UUID.randomUUID(), fondeo, destino.bloquear(),
                soles("10"), AHORA), MotivoDeRechazo.CUENTA_NO_OPERATIVA);
    }

    @Test
    @DisplayName("no se puede construir un movimiento que cree o destruya dinero")
    void laPartidaDobleEsUnInvariante() {
        UUID id = UUID.randomUUID();
        Asiento cargo = new Asiento(UUID.randomUUID(), origen.id(), id, TipoDeAsiento.CARGO,
                soles("10"), "x", AHORA);
        Asiento abonoMayor = new Asiento(UUID.randomUUID(), destino.id(), id, TipoDeAsiento.ABONO,
                soles("11"), "x", AHORA);
        Asiento otroCargo = new Asiento(UUID.randomUUID(), destino.id(), id, TipoDeAsiento.CARGO,
                soles("10"), "x", AHORA);
        Asiento abonoAjeno = new Asiento(UUID.randomUUID(), destino.id(), UUID.randomUUID(),
                TipoDeAsiento.ABONO, soles("10"), "x", AHORA);

        assertThatThrownBy(() -> new Movimiento(id, TipoDeMovimiento.TRANSFERENCIA, cargo, abonoMayor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no cuadra");
        assertThatThrownBy(() -> new Movimiento(id, TipoDeMovimiento.TRANSFERENCIA, cargo, otroCargo))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Movimiento(id, TipoDeMovimiento.TRANSFERENCIA, cargo, abonoAjeno))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cada motivo de rechazo trae un texto para el cliente")
    void motivosConTexto() {
        for (MotivoDeRechazo motivo : MotivoDeRechazo.values()) {
            assertThat(motivo.titulo()).isNotBlank();
            assertThat(motivo.detalle()).isNotBlank();
            assertThat(new OperacionRechazadaException(motivo).getMessage()).isEqualTo(motivo.detalle());
        }
    }
}
