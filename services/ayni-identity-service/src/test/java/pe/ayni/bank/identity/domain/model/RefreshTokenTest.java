package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rotación de tokens y detección de reutilización · HU-04 escenario 4. */
class RefreshTokenTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final UUID USUARIO = UUID.randomUUID();

    private static RefreshToken nuevo() {
        return RefreshToken.abrirFamilia(UUID.randomUUID(), USUARIO, "huella-1", AHORA);
    }

    @Test
    void unTokenReciennacidoEstaVigente() {
        RefreshToken token = nuevo();

        assertThat(token.estaVigente(AHORA)).isTrue();
        assertThat(token.estaConsumido()).isFalse();
        assertThat(token.expiraEn()).isEqualTo(AHORA.plus(RefreshToken.VIGENCIA));
    }

    @Test
    @DisplayName("rotar consume el actual y devuelve un sucesor de la misma familia")
    void rotarMantieneLaFamilia() {
        RefreshToken token = nuevo();

        RefreshToken.Rotacion rotacion =
                token.rotar(UUID.randomUUID(), "huella-2", AHORA.plusSeconds(60));

        assertThat(rotacion.consumido().estaConsumido()).isTrue();
        assertThat(rotacion.sucesor().estaConsumido()).isFalse();
        assertThat(rotacion.sucesor().familiaId()).isEqualTo(token.familiaId());
        assertThat(rotacion.sucesor().usuarioId()).isEqualTo(USUARIO);
    }

    @Test
    @DisplayName("el sucesor hereda la caducidad en lugar de reiniciarla")
    void laRotacionNoAlargaLaSesion() {
        // Si cada renovación empezara a contar de nuevo, una sesión renovada cada seis días
        // no caducaría jamás y los siete días del criterio serían decorativos.
        RefreshToken token = nuevo();

        RefreshToken sucesor = token
                .rotar(UUID.randomUUID(), "huella-2", AHORA.plusSeconds(60))
                .sucesor();

        assertThat(sucesor.expiraEn()).isEqualTo(token.expiraEn());
    }

    @Test
    @DisplayName("presentar un token ya consumido delata que existe una copia")
    void detectaLaReutilizacion() {
        RefreshToken consumido = nuevo()
                .rotar(UUID.randomUUID(), "huella-2", AHORA.plusSeconds(60))
                .consumido();

        assertThatThrownBy(() -> consumido.rotar(
                UUID.randomUUID(), "huella-3", AHORA.plusSeconds(120)))
                .isInstanceOfSatisfying(ReutilizacionDeRefreshTokenException.class, e ->
                        assertThat(e.familiaId()).isEqualTo(consumido.familiaId()));
    }

    @Test
    void unTokenCaducadoNoSePuedeRotar() {
        RefreshToken token = nuevo();

        assertThatThrownBy(() -> token.rotar(UUID.randomUUID(), "huella-2",
                AHORA.plus(RefreshToken.VIGENCIA)))
                .isInstanceOf(SesionExpiradaException.class);
    }

    @Test
    @DisplayName("la caducidad se comprueba antes que nada excepto la reutilizacion")
    void laReutilizacionPesaMasQueLaCaducidad() {
        // Un token robado, consumido y ademas caducado sigue siendo un incidente: hay que
        // tirar la familia, no limitarse a decir que la sesion vencio.
        RefreshToken consumido = nuevo()
                .rotar(UUID.randomUUID(), "huella-2", AHORA.plusSeconds(60))
                .consumido();

        assertThatThrownBy(() -> consumido.rotar(UUID.randomUUID(), "huella-3",
                AHORA.plus(RefreshToken.VIGENCIA).plusSeconds(1)))
                .isInstanceOf(ReutilizacionDeRefreshTokenException.class);
    }

    @Test
    void dosInstanciasConElMismoIdSonElMismoToken() {
        RefreshToken token = nuevo();
        RefreshToken releido = RefreshToken.reconstituir(token.id(), token.familiaId(),
                USUARIO, "huella-1", AHORA, token.expiraEn(), null);

        assertThat(token).isEqualTo(releido).hasSameHashCodeAs(releido);
    }

    @Test
    @DisplayName("toString no expone la huella: identifica una sesion activa")
    void noFiltraLaHuella() {
        assertThat(nuevo().toString()).doesNotContain("huella-1");
    }
}
