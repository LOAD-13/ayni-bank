package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenDeRecuperacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");
    private final UUID usuarioId = UUID.randomUUID();

    private TokenDeRecuperacion emitido() {
        return TokenDeRecuperacion.emitir(UUID.randomUUID(), usuarioId, "huella", AHORA);
    }

    @Test
    @DisplayName("vence a los 30 minutos de emitirse, como exige HU-21")
    void vigenciaDeTreintaMinutos() {
        TokenDeRecuperacion token = emitido();

        assertThat(TokenDeRecuperacion.VIGENCIA).isEqualTo(Duration.ofMinutes(30));
        assertThat(token.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(token.estaVigente(AHORA.plus(Duration.ofMinutes(29)))).isTrue();
        assertThat(token.estaVigente(AHORA.plus(Duration.ofMinutes(30)))).isFalse();
    }

    @Test
    @DisplayName("usarlo devuelve el token marcado con la hora de uso")
    void usar() {
        TokenDeRecuperacion usado = emitido().usar(AHORA.plusSeconds(60));

        assertThat(usado.usadoEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(usado.estaVigente(AHORA.plusSeconds(61))).isFalse();
    }

    @Test
    @DisplayName("un token ya usado no se puede volver a usar")
    void usoUnico() {
        TokenDeRecuperacion usado = emitido().usar(AHORA.plusSeconds(60));

        assertThatThrownBy(() -> usado.usar(AHORA.plusSeconds(90)))
                .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
    }

    @Test
    @DisplayName("un token caducado no se puede usar")
    void caducado() {
        TokenDeRecuperacion token = emitido();

        assertThatThrownBy(() -> token.usar(AHORA.plus(Duration.ofMinutes(31))))
                .isInstanceOf(EnlaceDeRecuperacionInvalidoException.class);
    }

    @Test
    @DisplayName("se reconstituye tal cual y no muestra la huella en las trazas")
    void reconstituirYTraza() {
        UUID id = UUID.randomUUID();
        TokenDeRecuperacion token = TokenDeRecuperacion.reconstituir(
                id, usuarioId, "huella-secreta", AHORA, AHORA.plusSeconds(1800), null);

        assertThat(token.id()).isEqualTo(id);
        assertThat(token.usuarioId()).isEqualTo(usuarioId);
        assertThat(token.huella()).isEqualTo("huella-secreta");
        assertThat(token.emitidoEn()).isEqualTo(AHORA);
        assertThat(token.toString()).doesNotContain("huella-secreta");
        assertThat(token).isEqualTo(TokenDeRecuperacion.reconstituir(
                id, usuarioId, "otra", AHORA, AHORA, AHORA)).hasSameHashCodeAs(token);
    }
}
