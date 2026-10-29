package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;

class AdaptadorRepositorioDeMetodoSegundoFactorTest {

    private MetodoSegundoFactorJpaRepository jpaRepository;
    private CifradorDeDatosPort cifrador;
    private AdaptadorRepositorioDeMetodoSegundoFactor adaptador;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(MetodoSegundoFactorJpaRepository.class);
        cifrador = mock(CifradorDeDatosPort.class);
        adaptador = new AdaptadorRepositorioDeMetodoSegundoFactor(jpaRepository, cifrador);
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

        Optional<MetodoDeSegundoFactor> resultado =
                adaptador.buscarPorUsuarioYTipo(usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(id);
        assertThat(resultado.get().usuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.get().tipo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        verify(cifrador, never()).descifrar(any());
    }

    @Test
    @DisplayName("listarPorUsuario devuelve la lista de métodos descifrando el secreto cuando existe")
    void listarPorUsuario() {
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        when(cifrador.descifrar("criptograma-sec1")).thenReturn("JBSWY3DPEHPK3PXP");
        MetodoSegundoFactorEntity e1 = new MetodoSegundoFactorEntity(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA, "criptograma-sec1", ahora, ahora);
        MetodoSegundoFactorEntity e2 = new MetodoSegundoFactorEntity(
                UUID.randomUUID(), usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, ahora, ahora);

        when(jpaRepository.findByUsuarioId(usuarioId)).thenReturn(List.of(e1, e2));

        List<MetodoDeSegundoFactor> resultado = adaptador.listarPorUsuario(usuarioId);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).tipo()).isEqualTo(TipoDeSegundoFactor.APP_AUTENTICADORA);
        assertThat(resultado.get(0).secreto()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(resultado.get(1).tipo()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(resultado.get(1).secreto()).isNull();
    }

    @Test
    @DisplayName("guardar persiste la entidad sin cifrar cuando no hay secreto")
    void guardarSinSecreto() {
        Instant ahora = Instant.now();
        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                UUID.randomUUID(), UUID.randomUUID(), TipoDeSegundoFactor.CORREO_ELECTRONICO, null, ahora);

        MetodoDeSegundoFactor resultado = adaptador.guardar(metodo);

        assertThat(resultado).isEqualTo(metodo);
        verify(jpaRepository).save(any(MetodoSegundoFactorEntity.class));
        verify(cifrador, never()).cifrar(any());
    }

    @Test
    @DisplayName("guardar cifra el secreto TOTP antes de persistir la entidad en JPA")
    void guardarConSecretoCifrado() {
        Instant ahora = Instant.now();
        when(cifrador.cifrar("JBSWY3DPEHPK3PXP")).thenReturn("aes-gcm-criptograma");
        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                UUID.randomUUID(), UUID.randomUUID(), TipoDeSegundoFactor.APP_AUTENTICADORA,
                "JBSWY3DPEHPK3PXP", ahora);

        MetodoDeSegundoFactor resultado = adaptador.guardar(metodo);

        assertThat(resultado).isEqualTo(metodo);
        ArgumentCaptor<MetodoSegundoFactorEntity> captor = ArgumentCaptor.forClass(MetodoSegundoFactorEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getSecreto()).isEqualTo("aes-gcm-criptograma");
    }
}

