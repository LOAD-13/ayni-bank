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

import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

@ExtendWith(MockitoExtension.class)
class AdaptadorRepositorioDeConfirmacionesTest {

    @Mock
    private ConfirmacionJpaRepository repositorio;

    @Test
    @DisplayName("una confirmacion guardada vuelve igual, con su metodo, desafio e intentos")
    void idaYVuelta() {
        ConfirmacionDeOperacion c = ConfirmacionDeOperacion.abrir(UUID.randomUUID(), UUID.randomUUID(), "huella",
                TipoDeSegundoFactor.CORREO_ELECTRONICO, UUID.randomUUID(), Instant.parse("2026-10-11T15:00:00Z"))
                .registrarFallo();
        var fila = ArgumentCaptor.forClass(ConfirmacionDeOperacionEntity.class);
        AdaptadorRepositorioDeConfirmaciones adaptador = new AdaptadorRepositorioDeConfirmaciones(repositorio);

        adaptador.guardar(c);
        verify(repositorio).save(fila.capture());
        when(repositorio.findById(c.id())).thenReturn(Optional.of(fila.getValue()));

        ConfirmacionDeOperacion leida = adaptador.buscar(c.id()).orElseThrow();
        assertThat(leida).isEqualTo(c);
        assertThat(leida.metodo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(leida.desafioCodigoId()).isEqualTo(c.desafioCodigoId());
        assertThat(leida.intentosFallidos()).isEqualTo(1);
        assertThat(leida.expiraEn()).isEqualTo(c.expiraEn());
        assertThat(leida.toString()).doesNotContain("huella");
    }
}
