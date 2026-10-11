package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Cci;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TasaVigente;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeInteresesPort;

/** Las reglas del cierre que no dependen de la base: dias pendientes, tasa ausente, idempotencia. */
class CierreDiarioServiceTest {

    // 16 de octubre, 03:00 en Lima: el ultimo dia terminado en Peru es el 15.
    private static final Instant AHORA = Instant.parse("2026-10-16T08:00:00Z");

    private final RegistroDeInteresesPort intereses = mock(RegistroDeInteresesPort.class);
    private final LibroMayorPort libro = mock(LibroMayorPort.class);
    private final PublicadorDeEventosPort eventos = mock(PublicadorDeEventosPort.class);
    private final PistaDeAuditoriaPort auditoria = mock(PistaDeAuditoriaPort.class);
    private final CierreDiarioService servicio = new CierreDiarioService(intereses, libro, eventos, auditoria,
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private static Cuenta cuenta(String numero) {
        NumeroDeCuenta n = new NumeroDeCuenta(numero);
        return Cuenta.reconstituir(UUID.randomUUID(), UUID.randomUUID(), (short) 1, n, Cci.para(n), Moneda.PEN,
                EstadoCuenta.ACTIVA, Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    @DisplayName("sin cierres previos empieza por ayer en hora de Lima; con cierres, recupera los dias que faltan")
    void diasPendientes() {
        when(intereses.ultimoCierre()).thenReturn(Optional.empty());
        assertThat(servicio.diasPendientes()).containsExactly(LocalDate.parse("2026-10-15"));

        when(intereses.ultimoCierre()).thenReturn(Optional.of(LocalDate.parse("2026-10-12")));
        assertThat(servicio.diasPendientes()).containsExactly(LocalDate.parse("2026-10-13"),
                LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-15"));

        when(intereses.ultimoCierre()).thenReturn(Optional.of(LocalDate.parse("2026-10-15")));
        assertThat(servicio.diasPendientes()).isEmpty();
    }

    @Test
    @DisplayName("un dia ya cerrado no se vuelve a procesar")
    void idempotente() {
        LocalDate dia = LocalDate.parse("2026-10-15");
        when(intereses.estaCerrado(dia)).thenReturn(true);

        servicio.cerrar(dia);

        verify(intereses, never()).cuentasQueDevengan(any());
        verify(intereses, never()).registrarCierre(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    @DisplayName("una cuenta sin tasa vigente no devenga, pero el dia se cierra")
    void sinTasa() {
        LocalDate dia = LocalDate.parse("2026-10-15");
        Cuenta ana = cuenta("00110000000010");
        when(intereses.cuentasQueDevengan(any())).thenReturn(List.of(ana));
        when(intereses.tasaVigente((short) 1, dia)).thenReturn(Optional.empty());

        servicio.cerrar(dia);

        verify(intereses, never()).registrar(any(Devengo.class));
        verify(intereses).registrarCierre(dia, 0, BigDecimal.ZERO, AHORA);
    }

    @Test
    @DisplayName("el ultimo dia del mes capitaliza, publica el evento y deja rastro en la auditoria")
    void finDeMes() {
        LocalDate dia = LocalDate.parse("2026-10-31");
        Cuenta ana = cuenta("00110000000010");
        Cuenta fuente = cuenta("00110000000003");
        when(intereses.cuentasQueDevengan(any())).thenReturn(List.of(ana));
        when(intereses.tasaVigente((short) 1, dia)).thenReturn(Optional.of(new TasaVigente(1,
                new BigDecimal("0.045"), new BigDecimal("0.045"), LocalDate.parse("2026-08-17"))));
        when(intereses.saldoAlCorte(any(), any(), any())).thenReturn(Dinero.de("1000.00", Moneda.PEN));
        when(intereses.cuentasConDevengoPendiente(dia)).thenReturn(List.of(ana.id()));
        when(intereses.devengadoSinCapitalizar(ana.id(), dia)).thenReturn(new BigDecimal("3.70000000"));
        when(intereses.ultimoResiduo(ana.id())).thenReturn(new BigDecimal("0.00000000"));
        when(libro.buscarPorId(ana.id())).thenReturn(Optional.of(ana));
        when(libro.cuentaDeIntereses(Moneda.PEN)).thenReturn(fuente);
        when(libro.saldoDe(ana.id(), Moneda.PEN)).thenReturn(Dinero.de("1003.70", Moneda.PEN));

        servicio.cerrar(dia);

        verify(libro).registrar(any());
        verify(eventos).registrar(org.mockito.ArgumentMatchers.eq("Movimiento"), any(),
                org.mockito.ArgumentMatchers.eq("InteresCapitalizado"), any());
        verify(auditoria).registrar(org.mockito.ArgumentMatchers.eq(TipoDeEventoDeOperacion.INTERES_CAPITALIZADO),
                org.mockito.ArgumentMatchers.eq(ana.usuarioId()), any());
        verify(intereses).registrar(any(pe.ayni.bank.core.domain.model.Capitalizacion.class),
                org.mockito.ArgumentMatchers.eq(dia), any());
        assertThat(CierreDiarioService.etiqueta(YearMonth.of(2026, 10))).isEqualTo("10/2026");
    }
}
