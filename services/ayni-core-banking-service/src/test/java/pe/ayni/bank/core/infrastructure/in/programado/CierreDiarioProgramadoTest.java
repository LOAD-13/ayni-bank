package pe.ayni.bank.core.infrastructure.in.programado;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.port.in.CerrarDiaUseCase;

class CierreDiarioProgramadoTest {

    private static final LocalDate D1 = LocalDate.parse("2026-10-14");
    private static final LocalDate D2 = LocalDate.parse("2026-10-15");
    private static final LocalDate D3 = LocalDate.parse("2026-10-16");

    @Test
    @DisplayName("cierra los dias pendientes en orden")
    void enOrden() {
        CerrarDiaUseCase cierre = mock(CerrarDiaUseCase.class);
        when(cierre.diasPendientes()).thenReturn(List.of(D1, D2));

        new CierreDiarioProgramado(cierre).cerrarLoPendiente();

        var orden = org.mockito.Mockito.inOrder(cierre);
        orden.verify(cierre).cerrar(D1);
        orden.verify(cierre).cerrar(D2);
    }

    @Test
    @DisplayName("si un dia falla, no cierra los siguientes")
    void paraEnElFallo() {
        CerrarDiaUseCase cierre = mock(CerrarDiaUseCase.class);
        when(cierre.diasPendientes()).thenReturn(List.of(D1, D2, D3));
        doThrow(new IllegalStateException("base caida")).when(cierre).cerrar(D2);

        new CierreDiarioProgramado(cierre).cerrarLoPendiente();

        verify(cierre).cerrar(D1);
        verify(cierre, never()).cerrar(D3);
    }
}
