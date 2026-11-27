package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SolicitudDeBloqueoTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    private static final UUID OPERADOR = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();

    private static SolicitudDeBloqueo pedida() {
        return SolicitudDeBloqueo.iniciar(UUID.randomUUID(), UUID.randomUUID(), "  Fraude reportado  ", OPERADOR, AHORA);
    }

    @Test
    @DisplayName("Nace pendiente, con el motivo recortado")
    void nacePendiente() {
        SolicitudDeBloqueo s = pedida();
        assertThat(s.pendiente()).isTrue();
        assertThat(s.motivo()).isEqualTo("Fraude reportado");
    }

    @Test
    @DisplayName("Otra persona la aprueba")
    void otraPersonaAprueba() {
        SolicitudDeBloqueo aprobada = pedida().aprobar(SUPERVISOR, AHORA.plusSeconds(60));
        assertThat(aprobada.pendiente()).isFalse();
        assertThat(aprobada.aprobadaPor()).isEqualTo(SUPERVISOR);
    }

    @Test
    @DisplayName("Quien la inicio no puede aprobarla")
    void cuatroOjos() {
        assertThatThrownBy(() -> pedida().aprobar(OPERADOR, AHORA))
                .isInstanceOf(CuatroOjosException.class);
        assertThatThrownBy(() -> new SolicitudDeBloqueo(UUID.randomUUID(), UUID.randomUUID(), "x", OPERADOR, AHORA,
                OPERADOR, AHORA)).isInstanceOf(CuatroOjosException.class);
    }

    @Test
    @DisplayName("No se aprueba dos veces")
    void noDosVeces() {
        SolicitudDeBloqueo aprobada = pedida().aprobar(SUPERVISOR, AHORA);
        assertThatThrownBy(() -> aprobada.aprobar(UUID.randomUUID(), AHORA))
                .isInstanceOf(SolicitudDeBloqueoNoValidaException.class);
    }

    @Test
    @DisplayName("Exige un motivo de hasta 200 caracteres")
    void motivo() {
        assertThatThrownBy(() -> SolicitudDeBloqueo.iniciar(UUID.randomUUID(), UUID.randomUUID(), " ", OPERADOR, AHORA))
                .isInstanceOf(SolicitudDeBloqueoNoValidaException.class);
        assertThatThrownBy(() -> SolicitudDeBloqueo.iniciar(UUID.randomUUID(), UUID.randomUUID(), "x".repeat(201),
                OPERADOR, AHORA)).isInstanceOf(SolicitudDeBloqueoNoValidaException.class);
        assertThatThrownBy(() -> new SolicitudDeBloqueo(UUID.randomUUID(), UUID.randomUUID(), "x", OPERADOR, AHORA,
                SUPERVISOR, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
