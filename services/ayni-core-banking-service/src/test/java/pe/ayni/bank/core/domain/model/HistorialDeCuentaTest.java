package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HistorialDeCuentaTest {

    private static final Instant ALTA = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant BLOQUEO = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID SUPERVISOR = UUID.randomUUID();
    private static final List<VersionDeCuenta> VERSIONES = List.of(
            new VersionDeCuenta(5, BLOQUEO, SUPERVISOR, VersionDeCuenta.Tipo.CAMBIO, "00110000000101",
                    EstadoCuenta.BLOQUEADA, Moneda.PEN),
            new VersionDeCuenta(1, ALTA, null, VersionDeCuenta.Tipo.ALTA, "00110000000101", EstadoCuenta.ACTIVA, Moneda.PEN));

    @Test
    @DisplayName("Entre dos cambios devuelve la version anterior")
    void entreCambios() {
        HistorialDeCuenta h = HistorialDeCuenta.en(Instant.parse("2026-11-22T00:00:00Z"), VERSIONES);
        assertThat(h.vigente()).hasValueSatisfying(v -> assertThat(v.estado()).isEqualTo(EstadoCuenta.ACTIVA));
        assertThat(h.versiones()).extracting(VersionDeCuenta::revision).containsExactly(1L, 5L);
    }

    @Test
    @DisplayName("En el instante exacto del cambio ya vale la version nueva, con su actor")
    void enElInstanteDelCambio() {
        HistorialDeCuenta h = HistorialDeCuenta.en(BLOQUEO, VERSIONES);
        assertThat(h.vigente()).hasValueSatisfying(v -> {
            assertThat(v.estado()).isEqualTo(EstadoCuenta.BLOQUEADA);
            assertThat(v.actor()).isEqualTo(SUPERVISOR);
        });
    }

    @Test
    @DisplayName("Antes de la primera version no hay estado conocido")
    void antesDelHistorial() {
        assertThat(HistorialDeCuenta.en(ALTA.minusSeconds(1), VERSIONES).vigente()).isEmpty();
    }
}
