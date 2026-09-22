package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

class AdaptadorRepositorioDeMetodoSegundoFactorTest {

    private MetodoSegundoFactorJpaRepository jpaRepository;
    private AdaptadorRepositorioDeMetodoSegundoFactor adaptador;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(MetodoSegundoFactorJpaRepository.class);
        adaptador = new AdaptadorRepositorioDeMetodoSegundoFactor(jpaRepository);
    }

    @Test
    @DisplayName("buscarPorUsuarioYTipo convierte la entidad JPA a dominio")
    void buscarPorUsuarioYTipo() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        MetodoSegundoFactorEntity entity = new MetodoSegundoFactorEntity(
                id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, ahora, ahora);

        when(jpaRepository.findByUsuarioIdAndTipo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(entity));

        Optional<MetodoDeSegundoFactor> resultado = adaptador.buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(id);
        assertThat(resultado.get().usuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.get().tipo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
    }

    @Test
    @DisplayName("listarPorUsuario devuelve la lista de métodos de dominio")
    void listarPorUsuario() {
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        MetodoSegundoFactorEntity e1 = new MetodoSegundoFactorEntity(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA, "sec1", ahora, ahora);
        MetodoSegundoFactorEntity e2 = new MetodoSegundoFactorEntity(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, ahora, ahora);

        when(jpaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(e1, e2));

        List<MetodoDeSegundoFactor> resultado = adaptador.listarPorUsuario(usuarioId);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).tipo()).isEqualTo(TipoDeSegundoFactor.APP_AUTENTICADORA);
        assertThat(resultado.get(1).tipo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
    }

    @Test
    @DisplayName("guardar persiste la entidad en el repositorio JPA")
    void guardar() {
        Instant ahora = Instant.now();
        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                UUID.randomUUID(), UUID.randomUUID(), TipoDeSegundoFactor.SMS, null, ahora);

        MetodoDeSegundoFactor resultado = adaptador.guardar(metodo);

        assertThat(resultado).isEqualTo(metodo);
        verify(jpaRepository).save(any(MetodoSegundoFactorEntity.class));
    }
}
