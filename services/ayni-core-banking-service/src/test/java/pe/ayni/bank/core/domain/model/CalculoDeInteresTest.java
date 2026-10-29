package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-06: precision del devengo y de la capitalizacion. */
class CalculoDeInteresTest {

    private static final Instant AHORA = Instant.parse("2026-10-16T05:05:00Z");
    private static final TasaVigente CINCO = new TasaVigente(7, new BigDecimal("0.050000"),
            new BigDecimal("0.050000"), LocalDate.parse("2026-09-01"));

    @Test
    @DisplayName("el factor diario coincide con (1.05)^(1/360) − 1 en 30 cifras")
    void factor() {
        // Valor de referencia calculado aparte con 50 digitos de precision.
        assertThat(CalculoDeInteres.factorDiario(new BigDecimal("0.05")).setScale(30, java.math.RoundingMode.HALF_EVEN))
                .isEqualByComparingTo("0.000135537418169965746693808824");
        assertThat(CalculoDeInteres.factorDiario(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> CalculoDeInteres.factorDiario(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("escenario 1: S/ 1000 al 5 % rinden 0.13553742 en el dia, con 8 decimales")
    void escenario1() {
        BigDecimal interes = CalculoDeInteres.interesDelDia(new BigDecimal("1000.00"), new BigDecimal("0.05"));
        assertThat(interes).isEqualTo(new BigDecimal("0.13553742"));
        assertThat(CalculoDeInteres.interesDelDia(new BigDecimal("1234.56"), new BigDecimal("0.045")))
                .isEqualTo(new BigDecimal("0.15095780"));
    }

    @Test
    @DisplayName("la raiz de Newton recupera la base al elevarla")
    void raiz() {
        BigDecimal r = CalculoDeInteres.raiz(new BigDecimal("1.045"), 360);
        assertThat(r.pow(360, java.math.MathContext.DECIMAL128).subtract(new BigDecimal("1.045")).abs())
                .isLessThan(new BigDecimal("1E-30"));
    }

    @Test
    @DisplayName("escenario 4: sin saldo no hay devengo")
    void sinSaldo() {
        UUID cuenta = UUID.randomUUID();
        LocalDate dia = LocalDate.parse("2026-10-15");
        assertThat(Devengo.calcular(cuenta, dia, Dinero.cero(Moneda.PEN), CINCO, AHORA)).isEmpty();
        assertThat(Devengo.calcular(cuenta, dia, Dinero.de("-5.00", Moneda.PEN), CINCO, AHORA)).isEmpty();

        Devengo d = Devengo.calcular(cuenta, dia, Dinero.de("1000.00", Moneda.PEN), CINCO, AHORA).orElseThrow();
        assertThat(d.tasaId()).isEqualTo(7);
        assertThat(d.tea()).isEqualByComparingTo("0.05");
        assertThat(d.importe()).isEqualTo(new BigDecimal("0.13553742"));
    }

    @Test
    @DisplayName("la capitalizacion abona a centimos con HALF_EVEN y guarda la fraccion para el mes siguiente")
    void capitalizacion() {
        UUID cuenta = UUID.randomUUID();
        YearMonth octubre = YearMonth.of(2026, 10);

        Capitalizacion c = Capitalizacion.de(cuenta, octubre, new BigDecimal("4.20166002"),
                new BigDecimal("0.00000000"), Moneda.PEN);
        assertThat(c.abonado()).isEqualTo(Dinero.de("4.20", Moneda.PEN));
        assertThat(c.residuo()).isEqualTo(new BigDecimal("0.00166002"));
        assertThat(c.hayQueAbonar()).isTrue();

        // El residuo de un mes se suma al siguiente: tres meses de 0.004 terminan abonando un centimo.
        Capitalizacion uno = Capitalizacion.de(cuenta, octubre, new BigDecimal("0.00400000"), BigDecimal.ZERO
                .setScale(8), Moneda.PEN);
        assertThat(uno.hayQueAbonar()).isFalse();
        assertThat(uno.movimientoId()).isNull();
        Capitalizacion dos = Capitalizacion.de(cuenta, octubre.plusMonths(1), new BigDecimal("0.00400000"),
                uno.residuo(), Moneda.PEN);
        assertThat(dos.abonado()).isEqualTo(Dinero.de("0.01", Moneda.PEN));
        assertThat(dos.residuo()).isEqualTo(new BigDecimal("-0.00200000"));
        Capitalizacion tres = Capitalizacion.de(cuenta, octubre.plusMonths(2), new BigDecimal("0.00400000"),
                dos.residuo(), Moneda.PEN);
        assertThat(tres.abonado().esCero()).isTrue();
        assertThat(tres.residuo()).isEqualTo(new BigDecimal("0.00200000"));
    }

    @Test
    @DisplayName("el movimiento de capitalizacion cuadra, no exige el minimo y rechaza importes no positivos")
    void movimiento() {
        Cuenta intereses = cuenta("00110000000003");
        Cuenta ana = cuenta("00110000000010");
        Movimiento m = Movimiento.capitalizacionDeIntereses(UUID.randomUUID(), intereses, ana,
                Dinero.de("0.01", Moneda.PEN), "10/2026", AHORA);

        assertThat(m.tipo()).isEqualTo(TipoDeMovimiento.INTERES);
        assertThat(m.cargo().cuentaId()).isEqualTo(intereses.id());
        assertThat(m.abono().concepto()).isEqualTo("Intereses ganados · 10/2026");
        Dinero cero = Dinero.cero(Moneda.PEN);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> Movimiento.capitalizacionDeIntereses(id, intereses, ana, cero, "10/2026", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        Dinero dolares = Dinero.de("1.00", Moneda.USD);
        assertThatThrownBy(() -> Movimiento.capitalizacionDeIntereses(id, intereses, ana, dolares, "10/2026", AHORA))
                .isInstanceOf(OperacionRechazadaException.class);
    }

    private static Cuenta cuenta(String numero) {
        NumeroDeCuenta n = new NumeroDeCuenta(numero);
        return Cuenta.reconstituir(UUID.randomUUID(), UUID.randomUUID(), (short) 1, n, Cci.para(n), Moneda.PEN,
                EstadoCuenta.ACTIVA, AHORA);
    }
}
