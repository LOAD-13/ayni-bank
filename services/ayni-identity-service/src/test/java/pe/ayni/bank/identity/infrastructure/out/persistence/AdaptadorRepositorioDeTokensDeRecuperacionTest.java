package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.TokenDeRecuperacion;

/** HU-21: lo que se guarda vuelve igual, y el uso del token depende de la base. */
@ExtendWith(MockitoExtension.class)
class AdaptadorRepositorioDeTokensDeRecuperacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-10T15:00:00Z");

    @Mock
    private TokenDeRecuperacionJpaRepository repositorio;

    private final UUID usuarioId = UUID.randomUUID();

    private AdaptadorRepositorioDeTokensDeRecuperacion adaptador() {
        return new AdaptadorRepositorioDeTokensDeRecuperacion(repositorio);
    }

    @Test
    @DisplayName("un token guardado se lee con su usuario, huella y fechas")
    void idaYVuelta() {
        TokenDeRecuperacion token = TokenDeRecuperacion.emitir(UUID.randomUUID(), usuarioId, "huella", AHORA);
        var fila = ArgumentCaptor.forClass(TokenDeRecuperacionEntity.class);

        adaptador().guardar(token);
        verify(repositorio).save(fila.capture());
        when(repositorio.findByHuellaAndAnuladoEnIsNull("huella")).thenReturn(Optional.of(fila.getValue()));

        TokenDeRecuperacion leido = adaptador().buscarPorHuella("huella").orElseThrow();
        assertThat(leido).isEqualTo(token);
        assertThat(leido.usuarioId()).isEqualTo(usuarioId);
        assertThat(leido.expiraEn()).isEqualTo(token.expiraEn());
        assertThat(leido.usadoEn()).isNull();
        assertThat(fila.getValue().getAnuladoEn()).isNull();
    }

    @Test
    @DisplayName("contar, anular y marcar delegan en consultas de una sola sentencia")
    void consultas() {
        when(repositorio.countByUsuarioIdAndEmitidoEnGreaterThanEqual(usuarioId, AHORA)).thenReturn(2L);
        UUID tokenId = UUID.randomUUID();
        when(repositorio.marcarUsado(tokenId, AHORA)).thenReturn(1, 0);

        assertThat(adaptador().contarEmitidosDesde(usuarioId, AHORA)).isEqualTo(2L);
        adaptador().anularPendientesDe(usuarioId, AHORA);
        verify(repositorio).anularPendientesDe(usuarioId, AHORA);
        assertThat(adaptador().marcarUsado(tokenId, AHORA)).isTrue();
        assertThat(adaptador().marcarUsado(tokenId, AHORA)).isFalse();
    }
}
