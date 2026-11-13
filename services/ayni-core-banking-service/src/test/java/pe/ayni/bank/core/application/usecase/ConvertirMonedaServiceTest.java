package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeConversionesPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeIdempotenciaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

class ConvertirMonedaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final TipoDeCambioAplicado VENTA = new TipoDeCambioAplicado(new BigDecimal("3.70"), AHORA, false);

    private RepositorioDeCuentasPort cuentas;
    private LibroMayorPort libro;
    private RegistroDeConversionesPort conversiones;
    private RegistroDeIdempotenciaPort idempotencia;
    private PistaDeAuditoriaPort auditoria;
    private ConvertirMonedaService servicio;

    @BeforeEach
    void preparar() {
        cuentas = mock(RepositorioDeCuentasPort.class);
        libro = mock(LibroMayorPort.class);
        conversiones = mock(RegistroDeConversionesPort.class);
        idempotencia = mock(RegistroDeIdempotenciaPort.class);
        auditoria = mock(PistaDeAuditoriaPort.class);
        servicio = new ConvertirMonedaService(cuentas, libro, conversiones, idempotencia, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Sin cuenta en la moneda de destino se rechaza y queda en la pista de auditoria")
    void sinCuentaDestino() {
        UUID titular = UUID.randomUUID();
        when(idempotencia.resultadoDe(any())).thenReturn(Optional.empty());
        when(cuentas.buscarActivaDe(titular, Moneda.PEN)).thenReturn(Optional.of(
                Cuenta.abrir(UUID.randomUUID(), titular, (short) 1,
                        new NumeroDeCuenta("00110000000101"), Moneda.PEN, AHORA)));
        when(cuentas.buscarActivaDe(titular, Moneda.USD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.convertir(titular, Dinero.de("10.00", Moneda.PEN), VENTA, UUID.randomUUID()))
                .isInstanceOf(OperacionRechazadaException.class);
        verify(auditoria).registrarRechazo(titular, MotivoDeRechazo.SIN_CUENTA);
        verify(libro, never()).registrar(any());
    }

    @Test
    @DisplayName("Una clave de idempotencia de otro titular no devuelve su conversion")
    void claveDeOtroTitular() {
        UUID salida = UUID.randomUUID();
        Conversion ajena = mock(Conversion.class);
        when(ajena.usuarioId()).thenReturn(UUID.randomUUID());
        when(idempotencia.resultadoDe(any())).thenReturn(Optional.of(salida));
        when(conversiones.buscarPorSalida(salida)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.convertir(UUID.randomUUID(), Dinero.de("10.00", Moneda.PEN), VENTA,
                UUID.randomUUID()))
                .isInstanceOf(OperarCuentaService.ClaveDeIdempotenciaReutilizadaException.class);
        verify(libro, never()).registrar(any());
    }
}
