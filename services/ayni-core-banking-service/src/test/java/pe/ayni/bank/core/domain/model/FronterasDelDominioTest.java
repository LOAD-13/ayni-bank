package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las fronteras del dominio monetario que las pruebas por camino feliz no tocaban (AYNI-304).
 * Salen de los mutantes que PIT dejó vivos: un `<` cambiado por `<=` o una validación quitada
 * no rompía ninguna prueba. Cada caso fija el valor que está justo en el borde, a cada lado.
 */
class FronterasDelDominioTest {

    private static final Instant AHORA = Instant.parse("2026-11-24T15:00:00Z");
    private static final UUID TITULAR = UUID.randomUUID();

    private static Cuenta cuenta(Moneda moneda, long correlativo) {
        return Cuenta.abrir(UUID.randomUUID(), TITULAR, (short) (moneda == Moneda.PEN ? 1 : 2),
                NumeroDeCuenta.de(moneda, correlativo), moneda, AHORA);
    }

    // ── Dinero y conversión ───────────────────────────────────────────────

    @Test
    @DisplayName("Restar importes de monedas distintas lanza, no devuelve una cifra sin sentido")
    void restarMonedasDistintas() {
        Dinero soles = Dinero.de("10.00", Moneda.PEN);
        Dinero dolares = Dinero.de("1.00", Moneda.USD);

        assertThatThrownBy(() -> soles.menos(dolares))
                .isInstanceOf(MonedasIncompatiblesException.class);
    }

    @Test
    @DisplayName("Dinero se imprime con su moneda y sus dos decimales")
    void dineroSeImprime() {
        assertThat(Dinero.de("12.5", Moneda.PEN).toString()).contains("12.50").contains("PEN");
    }

    @Test
    @DisplayName("Una conversión exige que las dos cuentas del cliente admitan movimientos")
    void conversionConCuentaBloqueada() {
        Cuenta soles = cuenta(Moneda.PEN, 101);
        Cuenta dolares = cuenta(Moneda.USD, 102);
        Cuenta posicionPen = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 4), Moneda.PEN, AHORA);
        Cuenta posicionUsd = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 2,
                NumeroDeCuenta.de(Moneda.USD, 4), Moneda.USD, AHORA);
        Cotizacion cotizacion = new Cotizacion(new TipoDeCambioAplicado(new BigDecimal("3.70"), AHORA, false),
                new BigDecimal("3.70"), new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005"), BigDecimal.ZERO),
                "BCRP");
        Dinero importe = Dinero.de("100.00", Moneda.PEN);
        Dinero saldo = Dinero.de("500.00", Moneda.PEN);

        assertThatThrownBy(() -> Conversion.de(UUID.randomUUID(), soles.bloquear(), saldo, dolares, posicionPen,
                posicionUsd, importe, cotizacion, AHORA))
                .isInstanceOfSatisfying(OperacionRechazadaException.class,
                        e -> assertThat(e.motivo()).isEqualTo(MotivoDeRechazo.CUENTA_NO_OPERATIVA));
        assertThatThrownBy(() -> Conversion.de(UUID.randomUUID(), soles, saldo, dolares.bloquear(), posicionPen,
                posicionUsd, importe, cotizacion, AHORA))
                .isInstanceOfSatisfying(OperacionRechazadaException.class,
                        e -> assertThat(e.motivo()).isEqualTo(MotivoDeRechazo.CUENTA_NO_OPERATIVA));
    }

    // ── Asiento ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("El concepto admite exactamente 140 caracteres y rechaza 141")
    void conceptoEnElLimite() {
        String justo = "x".repeat(140);
        UUID id = UUID.randomUUID();
        assertThatCode(() -> new Asiento(id, id, id, TipoDeAsiento.CARGO, Dinero.de("1.00", Moneda.PEN), justo, AHORA))
                .doesNotThrowAnyException();
        Dinero uno = Dinero.de("1.00", Moneda.PEN);
        String largo = justo + "x";
        assertThatThrownBy(() -> new Asiento(id, id, id, TipoDeAsiento.CARGO, uno, largo, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Cuenta ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("La cuenta expone su producto y su fecha de apertura")
    void cuentaExponeSusDatos() {
        Cuenta soles = cuenta(Moneda.PEN, 201);
        Cuenta dolares = cuenta(Moneda.USD, 202);

        assertThat(soles.productoId()).isEqualTo((short) 1);
        assertThat(dolares.productoId()).isEqualTo((short) 2);
        assertThat(soles.abiertaEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("Dos cuentas con el mismo id son la misma y comparten hash; con otro id, no")
    void cuentaIgualPorId() {
        UUID id = UUID.randomUUID();
        Cuenta una = Cuenta.reconstituir(id, TITULAR, (short) 1, NumeroDeCuenta.de(Moneda.PEN, 301),
                Cci.para(NumeroDeCuenta.de(Moneda.PEN, 301)), Moneda.PEN,
                EstadoCuenta.ACTIVA, AHORA);
        Cuenta otra = Cuenta.reconstituir(id, TITULAR, (short) 1, NumeroDeCuenta.de(Moneda.PEN, 301),
                Cci.para(NumeroDeCuenta.de(Moneda.PEN, 301)), Moneda.PEN,
                EstadoCuenta.BLOQUEADA, AHORA);

        assertThat(una).isEqualTo(otra).hasSameHashCodeAs(otra);
        assertThat(una).hasSameHashCodeAs(id);
        assertThat(una).isNotEqualTo(cuenta(Moneda.PEN, 301));
    }

    @Test
    @DisplayName("La cuenta se imprime sin el numero ni el CCI, que identifican al titular ante terceros")
    void cuentaNoFiltraSusDatos() {
        Cuenta soles = cuenta(Moneda.PEN, 401);

        assertThat(soles.toString()).contains(soles.id().toString()).contains("PEN")
                .doesNotContain(soles.numero().valor()).doesNotContain(soles.cci().valor());
    }

    // ── NumeroDeCuenta y Cci ──────────────────────────────────────────────

    @Test
    @DisplayName("El número se agrupa, se enmascara dejando los 4 últimos y se imprime tal cual")
    void numeroDeCuentaSePresenta() {
        NumeroDeCuenta numero = new NumeroDeCuenta("00119876543210");

        assertThat(numero.formateado()).isEqualTo("001-1987654-3-210");
        assertThat(numero.enmascarado()).isEqualTo("**********3210");
        assertThat(numero).hasToString("00119876543210");
    }

    @Test
    @DisplayName("El CCI se imprime agrupado y legible")
    void cciSeImprimeAgrupado() {
        Cci cci = Cci.para(new NumeroDeCuenta("00110000000101"));

        assertThat(cci.toString()).isEqualTo(cci.formateado()).matches("\\d{3}-\\d{3}-\\d{4}-\\d{4}-\\d{4}-\\d{2}");
    }

    // ── Tarjeta ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Congelar y descongelar cambian solo el estado y devuelven la tarjeta nueva")
    void congelarYDescongelar() {
        Tarjeta activa = Tarjeta.emitir(UUID.randomUUID(), TITULAR, new Pan("4999990000000007"), AHORA);

        Tarjeta congelada = activa.congelar();
        Tarjeta descongelada = congelada.descongelar();

        assertThat(congelada).isNotNull().isNotSameAs(activa);
        assertThat(congelada.estado()).isEqualTo(EstadoDeTarjeta.CONGELADA);
        assertThat(congelada.id()).isEqualTo(activa.id());
        assertThat(congelada.pan()).isEqualTo(activa.pan());
        assertThat(descongelada).isNotNull();
        assertThat(descongelada.estado()).isEqualTo(EstadoDeTarjeta.ACTIVA);
    }

    // ── Segmento, tipo oficial y filtros ──────────────────────────────────

    @Test
    @DisplayName("Un segmento admite margen cero y saldo mínimo cero, y rechaza lo negativo y el 100 %")
    void segmentoEnLosBordes() {
        assertThatCode(() -> new SegmentoCambiario("SIN_MARGEN", BigDecimal.ZERO, BigDecimal.ZERO))
                .doesNotThrowAnyException();
        assertThatCode(() -> new SegmentoCambiario("CASI", new BigDecimal("0.999999"), BigDecimal.ZERO))
                .doesNotThrowAnyException();
        BigDecimal saldoNegativo = new BigDecimal("-0.01");
        BigDecimal margenNegativo = new BigDecimal("-0.000001");
        assertThatThrownBy(() -> new SegmentoCambiario("X", BigDecimal.ZERO, saldoNegativo))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SegmentoCambiario("X", margenNegativo, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El tipo oficial admite compra igual a venta, pero no una venta menor")
    void tipoOficialCompraIgualVenta() {
        LocalDate dia = LocalDate.parse("2026-11-20");
        assertThatCode(() -> new TipoDeCambioOficial(dia, new BigDecimal("3.75"), new BigDecimal("3.75"), "BCRP"))
                .doesNotThrowAnyException();
        BigDecimal compra = new BigDecimal("3.75");
        BigDecimal ventaMenor = new BigDecimal("3.7499");
        assertThatThrownBy(() -> new TipoDeCambioOficial(dia, compra, ventaMenor, "BCRP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El tipo oficial nunca es cero: ni la compra, ni la venta")
    void tipoOficialNuncaCero() {
        LocalDate dia = LocalDate.parse("2026-11-20");
        assertThatThrownBy(() -> new TipoDeCambioOficial(dia, BigDecimal.ZERO, BigDecimal.ONE, "BCRP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TipoDeCambioOficial(dia, BigDecimal.ONE, BigDecimal.ZERO, "BCRP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TipoDeCambioOficial(dia, BigDecimal.ZERO, BigDecimal.ZERO, "BCRP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El filtro de la pista admite desde nulo o hasta nulo, pero no un rango vacío ni al revés")
    void filtroDeAuditoriaBordes() {
        Instant uno = Instant.parse("2026-11-01T05:00:00Z");
        Instant dos = Instant.parse("2026-11-02T05:00:00Z");

        assertThatCode(() -> new FiltroDeAuditoria(null, null, uno, dos)).doesNotThrowAnyException();
        assertThatCode(() -> new FiltroDeAuditoria(null, null, uno, null)).doesNotThrowAnyException();
        assertThatCode(() -> new FiltroDeAuditoria(null, null, null, dos)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new FiltroDeAuditoria(null, null, uno, uno))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeAuditoria(null, null, dos, uno))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La excepción del tipo cambiado conserva la cotización nueva y la moneda de origen")
    void excepcionDelTipoCambiado() {
        Cotizacion nueva = new Cotizacion(new TipoDeCambioAplicado(new BigDecimal("3.77"), AHORA, true),
                new BigDecimal("3.75"), new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005"), BigDecimal.ZERO),
                "BCRP");

        TipoDeCambioCambioException e = new TipoDeCambioCambioException(nueva, Moneda.USD);

        assertThat(e.nueva()).isSameAs(nueva);
        assertThat(e.origen()).isEqualTo(Moneda.USD);
    }

    // ── ControlesDeTarjeta ────────────────────────────────────────────────

    @Test
    @DisplayName("Si el consumo está en otra moneda, la excepción nombra esa moneda y no la del límite")
    void controlesNombranLaMonedaQueFalla() {
        Dinero limitePen = Dinero.de("300.00", Moneda.PEN);
        Dinero limiteUsd = Dinero.de("300.00", Moneda.USD);
        Dinero consumoPen = Dinero.de("1.00", Moneda.PEN);
        Dinero consumoUsd = Dinero.de("1.00", Moneda.USD);

        assertThatThrownBy(() -> new ControlesDeTarjeta(EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN,
                limitePen, consumoUsd, true))
                .isInstanceOf(MonedasIncompatiblesException.class).hasMessageContaining("USD");
        assertThatThrownBy(() -> new ControlesDeTarjeta(EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN,
                limiteUsd, consumoPen, true))
                .isInstanceOf(MonedasIncompatiblesException.class).hasMessageContaining("USD");
    }

    @Test
    @DisplayName("YearMonth de vencimiento: la vigencia es de 4 años")
    void vigencia() {
        Tarjeta t = Tarjeta.emitir(UUID.randomUUID(), TITULAR, new Pan("4999990000000007"), AHORA);
        assertThat(t.venceEn()).isEqualTo(YearMonth.of(2030, 11));
    }

    // ── Filtro del historial (HU-08) ──────────────────────────────────────

    private static Asiento asiento(String importe, String instante) {
        UUID id = UUID.randomUUID();
        return new Asiento(id, id, id, TipoDeAsiento.ABONO, Dinero.de(importe, Moneda.PEN), "x",
                Instant.parse(instante));
    }

    @Test
    @DisplayName("El filtro admite el mismo dia, el mismo importe, la pagina 0, el tamano 1 y el maximo")
    void filtroAdmiteLosBordes() {
        LocalDate dia = LocalDate.parse("2026-11-10");
        BigDecimal cien = new BigDecimal("100.00");

        assertThatCode(() -> new FiltroDeMovimientos(dia, dia, null, cien, cien, 0, 1)).doesNotThrowAnyException();
        assertThatCode(() -> new FiltroDeMovimientos(null, null, null, null, null, 0, 50)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, null, null, 0, 51))
                .isInstanceOf(IllegalArgumentException.class);
        BigDecimal cienYUnCentimo = new BigDecimal("100.01");
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, cienYUnCentimo, cien, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        LocalDate despues = dia.plusDays(1);
        assertThatThrownBy(() -> new FiltroDeMovimientos(despues, dia, null, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Los extremos del filtro son inclusivos: el importe minimo, el maximo y los dos dias cuentan")
    void filtroEsInclusivo() {
        FiltroDeMovimientos filtro = new FiltroDeMovimientos(LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-11-12"), null, new BigDecimal("50.00"), new BigDecimal("100.00"), 0, 20);

        assertThat(filtro.cumple(asiento("50.00", "2026-11-10T00:00:00Z"))).isTrue();
        assertThat(filtro.cumple(asiento("100.00", "2026-11-12T23:59:59Z"))).isTrue();
        assertThat(filtro.cumple(asiento("49.99", "2026-11-11T12:00:00Z"))).isFalse();
        assertThat(filtro.cumple(asiento("100.01", "2026-11-11T12:00:00Z"))).isFalse();
        assertThat(filtro.cumple(asiento("60.00", "2026-11-09T23:59:59Z"))).isFalse();
        assertThat(filtro.cumple(asiento("60.00", "2026-11-13T00:00:00Z"))).isFalse();
    }
}
