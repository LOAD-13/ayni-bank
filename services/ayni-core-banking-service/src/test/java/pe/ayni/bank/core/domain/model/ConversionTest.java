package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConversionTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final UUID TITULAR = UUID.randomUUID();
    private static final Cuenta SOLES = cuenta(TITULAR, "00110000000101", Moneda.PEN);
    private static final Cuenta DOLARES = cuenta(TITULAR, "00120000000102", Moneda.USD);
    private static final Cuenta POSICION_PEN = cuenta(UUID.randomUUID(), "00110000000004", Moneda.PEN);
    private static final Cuenta POSICION_USD = cuenta(UUID.randomUUID(), "00120000000004", Moneda.USD);
    private static final TipoDeCambioAplicado VENTA = new TipoDeCambioAplicado(new BigDecimal("3.70"), AHORA, false);

    private static Cuenta cuenta(UUID titular, String numero, Moneda moneda) {
        return Cuenta.abrir(UUID.randomUUID(), titular, (short) (moneda == Moneda.PEN ? 1 : 2),
                new NumeroDeCuenta(numero), moneda, AHORA);
    }

    private static Conversion soles(String importe, String saldo) {
        return Conversion.de(UUID.randomUUID(), SOLES, Dinero.de(saldo, Moneda.PEN), DOLARES,
                POSICION_PEN, POSICION_USD, Dinero.de(importe, Moneda.PEN), VENTA, AHORA);
    }

    @Test
    @DisplayName("Escenario 1: S/ 370.00 a 3.70 son US$ 100.00, en dos patas que cuadran cada una en su moneda")
    void conversionExitosa() {
        Conversion c = soles("370.00", "500.00");

        assertThat(c.importeOrigen()).isEqualTo(Dinero.de("370.00", Moneda.PEN));
        assertThat(c.importeDestino()).isEqualTo(Dinero.de("100.00", Moneda.USD));
        assertThat(c.salida().tipo()).isEqualTo(TipoDeMovimiento.CONVERSION);
        assertThat(c.salida().cargo().cuentaId()).isEqualTo(SOLES.id());
        assertThat(c.salida().abono().cuentaId()).isEqualTo(POSICION_PEN.id());
        assertThat(c.entrada().cargo().cuentaId()).isEqualTo(POSICION_USD.id());
        assertThat(c.entrada().abono().cuentaId()).isEqualTo(DOLARES.id());
        assertThat(c.usuarioId()).isEqualTo(TITULAR);
    }

    @Test
    @DisplayName("Ningun asiento mezcla monedas: cada pata va entera en una sola")
    void nuncaMezclaMonedas() {
        Conversion c = soles("370.00", "500.00");

        assertThat(c.salida().asientos()).allSatisfy(a -> assertThat(a.importe().moneda()).isEqualTo(Moneda.PEN));
        assertThat(c.entrada().asientos()).allSatisfy(a -> assertThat(a.importe().moneda()).isEqualTo(Moneda.USD));
        assertThat(c.asientos()).hasSize(4);
    }

    @Test
    @DisplayName("Escenario 3: con el importe de origen y el tipo aplicado se recalcula lo recibido")
    void recalculable() {
        Conversion c = soles("100.00", "500.00");

        BigDecimal recalculado = c.importeOrigen().importe()
                .divide(c.tipo().solesPorDolar(), 2, RoundingMode.HALF_EVEN);
        assertThat(c.importeDestino().importe()).isEqualByComparingTo(recalculado).isEqualByComparingTo("27.03");
    }

    @Test
    @DisplayName("De dolares a soles se multiplica por el tipo")
    void deDolaresASoles() {
        Conversion c = Conversion.de(UUID.randomUUID(), DOLARES, Dinero.de("50.00", Moneda.USD), SOLES,
                POSICION_USD, POSICION_PEN, Dinero.de("10.00", Moneda.USD),
                new TipoDeCambioAplicado(new BigDecimal("3.655"), AHORA, true), AHORA);

        assertThat(c.importeDestino()).isEqualTo(Dinero.de("36.55", Moneda.PEN));
        assertThat(c.tipo().enCache()).isTrue();
    }

    @Test
    @DisplayName("Sin saldo suficiente en la moneda de origen se rechaza")
    void saldoInsuficiente() {
        assertThatThrownBy(() -> soles("370.00", "100.00"))
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(MotivoDeRechazo.SALDO_INSUFICIENTE);
    }

    @Test
    @DisplayName("Entre dos cuentas de la misma moneda no hay conversion")
    void mismaMoneda() {
        assertThatThrownBy(() -> Conversion.de(UUID.randomUUID(), SOLES, Dinero.de("500.00", Moneda.PEN),
                SOLES, POSICION_PEN, POSICION_PEN, Dinero.de("10.00", Moneda.PEN), VENTA, AHORA))
                .isInstanceOf(OperacionRechazadaException.class);
    }

    @Test
    @DisplayName("Solo entre cuentas del mismo titular")
    void otroTitular() {
        Cuenta ajena = cuenta(UUID.randomUUID(), "00120000000103", Moneda.USD);
        assertThatThrownBy(() -> Conversion.de(UUID.randomUUID(), SOLES, Dinero.de("500.00", Moneda.PEN),
                ajena, POSICION_PEN, POSICION_USD, Dinero.de("10.00", Moneda.PEN), VENTA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Fuera de los limites de importe se rechaza")
    void fueraDeLimites() {
        assertThatThrownBy(() -> soles("0.50", "500.00"))
                .isInstanceOf(OperacionRechazadaException.class);
        assertThatThrownBy(() -> soles("5000.01", "9000.00"))
                .isInstanceOf(OperacionRechazadaException.class);
    }

    @Test
    @DisplayName("El tipo aplicado debe ser positivo")
    void tipoPositivo() {
        assertThatThrownBy(() -> new TipoDeCambioAplicado(BigDecimal.ZERO, AHORA, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
