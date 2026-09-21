package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.infrastructure.out.persistence.adapter.AdaptadorRepositorioDeDesafioPorCodigo;
import pe.ayni.bank.identity.infrastructure.out.persistence.entity.DesafioPorCodigoEntity;
import pe.ayni.bank.identity.infrastructure.out.persistence.repository.DesafioPorCodigoJpaRepository;

@ExtendWith(MockitoExtension.class)
class AdaptadorRepositorioDeDesafioPorCodigoTest {

    @Mock
    private DesafioPorCodigoJpaRepository repository;

    private AdaptadorRepositorioDeDesafioPorCodigo adaptador;

    private final UUID desafioId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final String hashSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @BeforeEach
    void setUp() {
        adaptador = new AdaptadorRepositorioDeDesafioPorCodigo(repository);
    }

    @Test
    @DisplayName("Guarda un desafío por código convirtiéndolo a entidad JPA")
    void guardar_MapeaAEntidad() {
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashSha256, Instant.now());

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DesafioPorCodigo guardado = adaptador.guardar(desafio);

        ArgumentCaptor<DesafioPorCodigoEntity> captor = ArgumentCaptor.forClass(DesafioPorCodigoEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getId()).isEqualTo(desafioId);
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(usuarioId);
        assertThat(captor.getValue().getTipoFactor()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(captor.getValue().getHashCodigo()).isEqualTo(hashSha256);
        assertThat(guardado.id()).isEqualTo(desafioId);
    }

    @Test
    @DisplayName("Busca por id devolviendo el modelo de dominio reconstituido")
    void buscarPorId_EncuentraYReconstituye() {
        DesafioPorCodigoEntity entity = DesafioPorCodigoEntity.desdeDominio(
                DesafioPorCodigo.generar(desafioId, usuarioId, TipoDeSegundoFactor.SMS, hashSha256, Instant.now()));

        when(repository.findById(desafioId)).thenReturn(Optional.of(entity));

        Optional<DesafioPorCodigo> encontrado = adaptador.buscarPorId(desafioId);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().id()).isEqualTo(desafioId);
        assertThat(encontrado.get().tipoFactor()).isEqualTo(TipoDeSegundoFactor.SMS);
    }

    @Test
    @DisplayName("Busca el último pendiente ordenado por fecha de creación desc")
    void buscarUltimoPendiente_EncuentraUltimo() {
        DesafioPorCodigoEntity entity = DesafioPorCodigoEntity.desdeDominio(
                DesafioPorCodigo.generar(desafioId, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hashSha256, Instant.now()));

        when(repository.findFirstByUsuarioIdAndTipoFactorOrderByCreadoEnDesc(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO))
                .thenReturn(Optional.of(entity));

        Optional<DesafioPorCodigo> encontrado = adaptador.buscarUltimoPendiente(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().usuarioId()).isEqualTo(usuarioId);
    }
}
