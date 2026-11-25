package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.ayni.bank.core.domain.model.FiltroDeAuditoria;
import pe.ayni.bank.core.domain.port.out.LecturaDeAuditoriaPort;

class ConsultarAuditoriaServiceTest {

    private final LecturaDeAuditoriaPort lectura = mock(LecturaDeAuditoriaPort.class);
    private final ConsultarAuditoriaService servicio = new ConsultarAuditoriaService(lectura);

    @Test
    @DisplayName("Los dias son de Lima y «hasta» incluye el dia entero")
    void diasDeLima() {
        servicio.consultar(null, null, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-24"), 0, 20);

        ArgumentCaptor<FiltroDeAuditoria> filtro = ArgumentCaptor.forClass(FiltroDeAuditoria.class);
        verify(lectura).buscar(filtro.capture(), anyInt(), anyInt());
        // Lima es UTC-5: el 1 de noviembre empieza a las 05:00 UTC y el 24 termina al llegar el 25.
        assertThat(filtro.getValue().desde()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        assertThat(filtro.getValue().hasta()).isEqualTo(Instant.parse("2026-11-25T05:00:00Z"));
    }

    @Test
    @DisplayName("Sin fechas no se acota, y el usuario y el tipo pasan tal cual")
    void sinFechas() {
        UUID usuario = UUID.randomUUID();
        servicio.consultar(usuario, "PAN_REVELADO", null, null, 3, 10);

        ArgumentCaptor<FiltroDeAuditoria> filtro = ArgumentCaptor.forClass(FiltroDeAuditoria.class);
        verify(lectura).buscar(filtro.capture(), eq(3), eq(10));
        assertThat(filtro.getValue()).isEqualTo(new FiltroDeAuditoria(usuario, "PAN_REVELADO", null, null));
    }

    @Test
    @DisplayName("Un rango al reves se rechaza antes de leer")
    void rangoAlReves() {
        assertThatThrownBy(() -> servicio.consultar(null, null, LocalDate.parse("2026-11-24"),
                LocalDate.parse("2026-11-01"), 0, 20)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(lectura);
    }
}
