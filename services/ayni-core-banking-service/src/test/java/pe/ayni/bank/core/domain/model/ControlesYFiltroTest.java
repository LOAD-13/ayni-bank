package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.application.usecase.ControlarTarjetaService;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;

class ControlesYFiltroTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    @DisplayName("PanRevelado nunca expone el numero completo en toString()")
    void panReveladoOcultaToString() {
        PanRevelado revelado = new PanRevelado("4111 1111 1111 4821", AHORA);
        assertThat(revelado.toString()).isEqualTo("PanRevelado[oculto]");
        assertThat(revelado.numero()).isEqualTo("4111 1111 1111 4821");
    }

    @Test
    @DisplayName("ControlesDeTarjeta valida invariantes de nulidad, positividad y moneda")
    void controlesDeTarjetaInvariantes() {
        Dinero limite = Dinero.de("500.00", Moneda.PEN);
        Dinero consumido = Dinero.de("100.00", Moneda.PEN);

        ControlesDeTarjeta ok = new ControlesDeTarjeta(
                EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN, limite, consumido, true);
        assertThat(ok.ultimos4()).isEqualTo("4821");

        assertThatThrownBy(() -> new ControlesDeTarjeta(
                EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN, Dinero.cero(Moneda.PEN), consumido, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ControlesDeTarjeta(
                EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN, limite, Dinero.de("-1.00", Moneda.PEN), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ControlesDeTarjeta(
                EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN, Dinero.de("500.00", Moneda.USD), consumido, true))
                .isInstanceOf(MonedasIncompatiblesException.class);
        assertThatThrownBy(() -> new ControlesDeTarjeta(
                EstadoDeTarjeta.ACTIVA, "4821", Moneda.PEN, limite, Dinero.de("10.00", Moneda.USD), true))
                .isInstanceOf(MonedasIncompatiblesException.class);
    }

    @Test
    @DisplayName("FiltroDeMovimientos valida invariantes y evalua cada criterio en cumple(Asiento)")
    void filtroDeMovimientosInvariantesYCumple() {
        LocalDate d1 = LocalDate.of(2026, 10, 5);
        LocalDate d2 = LocalDate.of(2026, 10, 15);

        assertThatThrownBy(() -> FiltroDeMovimientos.porFechas(d2, d1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeMovimientos(
                null, null, null, new BigDecimal("100"), new BigDecimal("50"), 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroDeMovimientos(null, null, null, null, null, 0, 51))
                .isInstanceOf(IllegalArgumentException.class);

        Asiento abonoOct10 = new Asiento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                TipoDeAsiento.ABONO, Dinero.de("50.00", Moneda.PEN), "Abono", AHORA);

        assertThat(FiltroDeMovimientos.porFechas(d1, d2).cumple(abonoOct10)).isTrue();
        assertThat(FiltroDeMovimientos.porFechas(LocalDate.of(2026, 10, 11), d2).cumple(abonoOct10)).isFalse();
        assertThat(FiltroDeMovimientos.porFechas(d1, LocalDate.of(2026, 10, 9)).cumple(abonoOct10)).isFalse();
        assertThat(new FiltroDeMovimientos(null, null, TipoDeAsiento.CARGO, null, null, 0, 20)
                .cumple(abonoOct10)).isFalse();
        assertThat(new FiltroDeMovimientos(null, null, TipoDeAsiento.ABONO, new BigDecimal("60.00"), null, 0, 20)
                .cumple(abonoOct10)).isFalse();
        assertThat(new FiltroDeMovimientos(null, null, TipoDeAsiento.ABONO, new BigDecimal("10.00"), new BigDecimal("40.00"), 0, 20)
                .cumple(abonoOct10)).isFalse();
        assertThat(new FiltroDeMovimientos(null, null, TipoDeAsiento.ABONO, new BigDecimal("10.00"), new BigDecimal("60.00"), 0, 20)
                .cumple(abonoOct10)).isTrue();

        AsientoConSaldo conSaldo = new AsientoConSaldo(abonoOct10, Dinero.de("150.00", Moneda.PEN));
        assertThat(conSaldo.saldoPosterior()).isEqualTo(Dinero.de("150.00", Moneda.PEN));
        assertThatThrownBy(() -> new AsientoConSaldo(null, Dinero.de("150.00", Moneda.PEN)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ControlarTarjetaService cubre validaciones de limite, saldo insuficiente y comercio vacio")
    void controlarTarjetaCasosBorde() {
        RepositorioDeTarjetasPort tarjetas = mock(RepositorioDeTarjetasPort.class);
        LibroMayorPort libro = mock(LibroMayorPort.class);
        PistaDeAuditoriaPort auditoria = mock(PistaDeAuditoriaPort.class);
        PublicadorDeEventosPort eventos = mock(PublicadorDeEventosPort.class);
        ControlarTarjetaService servicio = new ControlarTarjetaService(
                tarjetas, libro, auditoria, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));

        UUID usuarioId = UUID.randomUUID();
        assertThatThrownBy(() -> servicio.configurar(usuarioId, null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.configurar(usuarioId, Dinero.cero(Moneda.PEN), true))
                .isInstanceOf(IllegalArgumentException.class);

        when(tarjetas.deTitular(usuarioId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultarControles(usuarioId))
                .isInstanceOf(SinTarjetaException.class);

        Tarjeta activa = Tarjeta.emitir(UUID.randomUUID(), UUID.randomUUID(),
                new pe.ayni.bank.core.domain.service.GeneradorDePan(new java.util.Random(1)).generar(), AHORA);
        when(tarjetas.deTitular(usuarioId)).thenReturn(Optional.of(activa));
        when(tarjetas.controlesDe(any(), any())).thenReturn(new ControlesDeTarjeta(
                EstadoDeTarjeta.ACTIVA, activa.pan().ultimos4(), Moneda.PEN,
                Dinero.de("500.00", Moneda.PEN), Dinero.cero(Moneda.PEN), false));

        Cuenta origen = Cuenta.abrir(activa.cuentaId(), usuarioId, (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 1_000_000_050L), Moneda.PEN, AHORA);
        Cuenta fondeo = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 1_000_000_001L), Moneda.PEN, AHORA);
        when(libro.buscarPorId(activa.cuentaId())).thenReturn(Optional.of(origen));
        when(libro.cuentaDeFondeo(Moneda.PEN)).thenReturn(fondeo);
        when(libro.saldoDe(origen.id(), Moneda.PEN)).thenReturn(Dinero.de("10.00", Moneda.PEN));

        // Consumo presencial (porInternet=false) que supera el saldo disponible
        assertThatThrownBy(() -> servicio.consumir(usuarioId, Dinero.de("50.00", Moneda.PEN), false, "   "))
                .isInstanceOf(OperacionRechazadaException.class);

        // Consumo presencial exitoso con comercio en blanco
        when(libro.saldoDe(origen.id(), Moneda.PEN)).thenReturn(Dinero.de("200.00", Moneda.PEN));
        servicio.consumir(usuarioId, Dinero.de("50.00", Moneda.PEN), false, null);
    }
}
