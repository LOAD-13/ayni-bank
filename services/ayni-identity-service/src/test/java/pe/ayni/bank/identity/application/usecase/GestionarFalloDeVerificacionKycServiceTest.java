package pe.ayni.bank.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.IdentidadDeclarada;
import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * AYNI-13 subtarea 11: limite de tres intentos y derivacion a revision manual. Ver ADR-0021.
 */
class GestionarFalloDeVerificacionKycServiceTest {

    private SolicitudesFalsas solicitudes;
    private GestionarFalloDeVerificacionKycService servicio;
    private UUID solicitudId;

    @BeforeEach
    void prepararEscenario() {
        solicitudes = new SolicitudesFalsas();
        servicio = new GestionarFalloDeVerificacionKycService(solicitudes);
        solicitudId = UUID.randomUUID();
    }

    @Test
    @DisplayName("el primer y el segundo fallo dejan reintentar, sin tocar el estado")
    void losPrimerosFallosPermitenReintentar() {
        assertThat(servicio.registrarFalloDeUsuario(solicitudId))
                .isEqualTo(ResultadoDelIntentoKyc.PUEDE_REINTENTAR);
        assertThat(servicio.registrarFalloDeUsuario(solicitudId))
                .isEqualTo(ResultadoDelIntentoKyc.PUEDE_REINTENTAR);

        assertThat(solicitudes.intentos.get(solicitudId)).isEqualTo(2);
        assertThat(solicitudes.enRevisionManual).isEmpty();
    }

    @Test
    @DisplayName("el tercer fallo agota el limite y deriva a revision manual")
    void elTercerFalloDeriva() {
        servicio.registrarFalloDeUsuario(solicitudId);
        servicio.registrarFalloDeUsuario(solicitudId);

        ResultadoDelIntentoKyc resultado = servicio.registrarFalloDeUsuario(solicitudId);

        assertThat(resultado).isEqualTo(ResultadoDelIntentoKyc.DERIVADA_A_REVISION_MANUAL);
        assertThat(solicitudes.enRevisionManual).containsExactly(solicitudId);
    }

    @Test
    @DisplayName("la caida de kyc-service deriva de inmediato, sin gastar intentos")
    void laCaidaDelServicioDerivaSinContarIntentos() {
        servicio.derivarPorServicioNoDisponible(solicitudId);

        assertThat(solicitudes.enRevisionManual).containsExactly(solicitudId);
        // No paso por el contador de intentos: nunca se llamo a registrarIntentoFallidoDeKyc.
        assertThat(solicitudes.intentos).doesNotContainKey(solicitudId);
    }

    @Test
    @DisplayName("los intentos se cuentan por solicitud, no de forma global")
    void losIntentosSonPorSolicitud() {
        UUID otraSolicitud = UUID.randomUUID();

        servicio.registrarFalloDeUsuario(solicitudId);
        servicio.registrarFalloDeUsuario(solicitudId);
        servicio.registrarFalloDeUsuario(otraSolicitud);

        assertThat(solicitudes.intentos.get(solicitudId)).isEqualTo(2);
        assertThat(solicitudes.intentos.get(otraSolicitud)).isEqualTo(1);
        assertThat(solicitudes.enRevisionManual).isEmpty();
    }

    // ─── Doble ─────────────────────────────────────────────────────────────

    private static final class SolicitudesFalsas implements RepositorioDeSolicitudesPort {
        private final Map<UUID, Integer> intentos = new HashMap<>();
        private final List<UUID> enRevisionManual = new ArrayList<>();

        @Override
        public UUID abrirPara(UUID usuarioId, IdentidadDeclarada identidad) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public UUID abrirSenuelo() {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public Optional<UUID> titularDe(UUID solicitudId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public void marcarAprobada(UUID solicitudId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }

        @Override
        public int registrarIntentoFallidoDeKyc(UUID solicitudId) {
            return intentos.merge(solicitudId, 1, Integer::sum);
        }

        @Override
        public void marcarEnRevisionManual(UUID solicitudId) {
            enRevisionManual.add(solicitudId);
        }

        @Override
        public Optional<String> nombreDePilaDe(UUID usuarioId) {
            throw new UnsupportedOperationException("No usado en estas pruebas");
        }
    }
}
