package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.ayni.bank.core.domain.model.CuatroOjosException;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeSolicitudesDeBloqueoPort;

class BloquearCuentaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();
    private static final String NUMERO = "00110000000101";

    private LibroMayorPort libro;
    private RepositorioDeCuentasPort cuentas;
    private RepositorioDeSolicitudesDeBloqueoPort solicitudes;
    private PistaDeAuditoriaPort auditoria;
    private BloquearCuentaService servicio;
    private Cuenta cuenta;

    @BeforeEach
    void preparar() {
        libro = mock(LibroMayorPort.class);
        cuentas = mock(RepositorioDeCuentasPort.class);
        solicitudes = mock(RepositorioDeSolicitudesDeBloqueoPort.class);
        auditoria = mock(PistaDeAuditoriaPort.class);
        servicio = new BloquearCuentaService(libro, cuentas, solicitudes, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
        cuenta = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 1, new NumeroDeCuenta(NUMERO), Moneda.PEN, AHORA);
        when(libro.buscarPorNumero(new NumeroDeCuenta(NUMERO))).thenReturn(Optional.of(cuenta));
        when(libro.buscarPorId(cuenta.id())).thenReturn(Optional.of(cuenta));
    }

    @Test
    @DisplayName("Iniciar no toca la cuenta: deja la solicitud pendiente y el evento en la pista")
    void iniciar() {
        SolicitudDeBloqueo s = servicio.iniciar(OPERADOR, NUMERO, "Fraude reportado");

        assertThat(s.pendiente()).isTrue();
        verify(solicitudes).registrar(s);
        verify(auditoria).registrar(TipoDeEventoDeOperacion.BLOQUEO_SOLICITADO, OPERADOR, s.id());
        verify(cuentas, never()).guardar(any());
    }

    @Test
    @DisplayName("No se pide un bloqueo sobre una cuenta inexistente, inactiva o con otro pendiente")
    void iniciarNoValido() {
        assertThatThrownBy(() -> servicio.iniciar(OPERADOR, "00110000009999", "x"))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.iniciar(OPERADOR, "no-es-numero", "x"))
                .isInstanceOf(NoSuchElementException.class);
        when(solicitudes.existePendientePara(cuenta.id())).thenReturn(true);
        assertThatThrownBy(() -> servicio.iniciar(OPERADOR, NUMERO, "x"))
                .isInstanceOf(SolicitudDeBloqueoNoValidaException.class);
        when(libro.buscarPorNumero(new NumeroDeCuenta(NUMERO))).thenReturn(Optional.of(cuenta.bloquear()));
        assertThatThrownBy(() -> servicio.iniciar(OPERADOR, NUMERO, "x"))
                .isInstanceOf(SolicitudDeBloqueoNoValidaException.class);
    }

    @Test
    @DisplayName("Otra persona aprueba: la cuenta queda bloqueada y se registra")
    void aprobar() {
        SolicitudDeBloqueo pedida = SolicitudDeBloqueo.iniciar(UUID.randomUUID(), cuenta.id(), "Fraude", OPERADOR, AHORA);
        when(solicitudes.buscar(pedida.id())).thenReturn(Optional.of(pedida));

        SolicitudDeBloqueo aprobada = servicio.aprobar(SUPERVISOR, pedida.id());

        assertThat(aprobada.aprobadaPor()).isEqualTo(SUPERVISOR);
        verify(solicitudes).aprobar(aprobada);
        ArgumentCaptor<Cuenta> guardada = ArgumentCaptor.forClass(Cuenta.class);
        verify(cuentas).guardar(guardada.capture());
        assertThat(guardada.getValue().estado()).isEqualTo(EstadoCuenta.BLOQUEADA);
        verify(auditoria).registrar(TipoDeEventoDeOperacion.CUENTA_BLOQUEADA, SUPERVISOR, pedida.id());
    }

    @Test
    @DisplayName("Quien inicio no puede aprobar: se deniega, queda en la pista y la cuenta no cambia")
    void cuatroOjos() {
        SolicitudDeBloqueo pedida = SolicitudDeBloqueo.iniciar(UUID.randomUUID(), cuenta.id(), "Fraude", OPERADOR, AHORA);
        when(solicitudes.buscar(pedida.id())).thenReturn(Optional.of(pedida));

        assertThatThrownBy(() -> servicio.aprobar(OPERADOR, pedida.id())).isInstanceOf(CuatroOjosException.class);

        verify(auditoria).registrarCuatroOjosDenegado(OPERADOR, pedida.id());
        verify(solicitudes, never()).aprobar(any());
        verify(cuentas, never()).guardar(any());
    }

    @Test
    @DisplayName("Una solicitud que no existe da no encontrada")
    void noExiste() {
        when(solicitudes.buscar(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.aprobar(SUPERVISOR, UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
