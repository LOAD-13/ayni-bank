package pe.ayni.bank.identity.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.Celular;
import pe.ayni.bank.identity.domain.model.Consentimiento;
import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;
import pe.ayni.bank.identity.domain.model.CorreoElectronico;
import pe.ayni.bank.identity.domain.model.DesafioDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.EstadoUsuario;
import pe.ayni.bank.identity.domain.model.RefreshToken;
import pe.ayni.bank.identity.domain.model.Usuario;

/**
 * Sesiones y usuarios: que lo que se guarda vuelva igual al leerse, y que una fila corrupta
 * falle al reconstruirse en lugar de convertirse en un objeto de dominio invalido.
 */
@ExtendWith(MockitoExtension.class)
class AdaptadoresDeSesionYUsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private final UUID usuarioId = UUID.randomUUID();

    @Nested
    @DisplayName("Sesiones: desafios de segundo factor y tokens de renovacion")
    class Sesiones {

        @Mock
        private DesafioJpaRepository desafios;

        @Mock
        private RefreshTokenJpaRepository tokens;

        private AdaptadorRepositorioDeSesiones adaptador() {
            return new AdaptadorRepositorioDeSesiones(desafios, tokens, RELOJ);
        }

        @Test
        @DisplayName("un desafio guardado se lee con su usuario y su caducidad")
        void desafioIdaYVuelta() {
            DesafioDeSegundoFactor desafio = DesafioDeSegundoFactor.abrir(usuarioId, AHORA);
            var fila = ArgumentCaptor.forClass(DesafioEntity.class);

            adaptador().guardarDesafio(desafio);
            verify(desafios).save(fila.capture());
            when(desafios.findByIdAndConsumidoEnIsNull(desafio.id())).thenReturn(Optional.of(fila.getValue()));

            assertThat(adaptador().buscarDesafio(desafio.id())).contains(desafio);
        }

        @Test
        @DisplayName("consumir un desafio lo marca con la hora del reloj; uno inexistente no falla")
        void consumirDesafio() {
            DesafioEntity fila = new DesafioEntity(UUID.randomUUID(), usuarioId, AHORA, AHORA.plusSeconds(120));
            when(desafios.findById(fila.getId())).thenReturn(Optional.of(fila));

            adaptador().consumirDesafio(fila.getId());
            adaptador().consumirDesafio(UUID.randomUUID());

            assertThat(fila.getConsumidoEn()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("un token de renovacion vuelve con su familia, huella y fechas")
        void tokenIdaYVuelta() {
            RefreshToken token = RefreshToken.abrirFamilia(UUID.randomUUID(), usuarioId, "huella-sha256", AHORA);
            var fila = ArgumentCaptor.forClass(RefreshTokenEntity.class);

            assertThat(adaptador().guardarToken(token)).isSameAs(token);
            verify(tokens).save(fila.capture());
            when(tokens.findByHuellaAndInvalidadoEnIsNull("huella-sha256")).thenReturn(Optional.of(fila.getValue()));

            RefreshToken leido = adaptador().buscarTokenPorHuella("huella-sha256").orElseThrow();
            assertThat(leido.id()).isEqualTo(token.id());
            assertThat(leido.familiaId()).isEqualTo(token.familiaId());
            assertThat(leido.expiraEn()).isEqualTo(token.expiraEn());
            assertThat(leido.consumidoEn()).isNull();
        }

        @Test
        @DisplayName("invalidar una familia corta todas sus sesiones en el instante del reloj")
        void invalidarFamilia() {
            UUID familia = UUID.randomUUID();
            adaptador().invalidarFamilia(familia);
            verify(tokens).invalidarFamilia(familia, AHORA);
        }
    }

    @Nested
    @DisplayName("Usuarios")
    class Usuarios {

        @Mock
        private UsuarioJpaRepository repositorio;

        private AdaptadorRepositorioDeUsuarios adaptador() {
            return new AdaptadorRepositorioDeUsuarios(repositorio, RELOJ);
        }

        private Usuario ana() {
            return Usuario.registrar(usuarioId, new CorreoElectronico("ana.quispe@example.pe"),
                    new Celular("987654321"), new ContrasenaCifrada("$argon2id$loquesea"),
                    new Consentimiento(AHORA, "2026-08"), AHORA);
        }

        @Test
        @DisplayName("guardar y releer devuelve el mismo usuario, con todos sus datos")
        void idaYVuelta() {
            when(repositorio.save(any(UsuarioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            Usuario guardado = adaptador().guardar(ana());

            assertThat(guardado.id()).isEqualTo(usuarioId);
            assertThat(guardado.correo().valor()).isEqualTo("ana.quispe@example.pe");
            assertThat(guardado.celular().valor()).isEqualTo("987654321");
            assertThat(guardado.estado()).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
            assertThat(guardado.consentimiento().versionDeLosTerminos()).isEqualTo("2026-08");
        }

        @Test
        @DisplayName("busca por correo y por id, y responde si un correo ya existe")
        void busquedas() {
            UsuarioEntity fila = MapeadorDeUsuario.aEntidad(ana(), AHORA);
            when(repositorio.findByCorreo("ana.quispe@example.pe")).thenReturn(Optional.of(fila));
            when(repositorio.findById(usuarioId)).thenReturn(Optional.of(fila));
            when(repositorio.existsByCorreo("ana.quispe@example.pe")).thenReturn(true);
            CorreoElectronico correo = new CorreoElectronico("ana.quispe@example.pe");

            assertThat(adaptador().buscarPorCorreo(correo)).map(Usuario::id).contains(usuarioId);
            assertThat(adaptador().buscarPorId(usuarioId)).isPresent();
            assertThat(adaptador().existeCorreo(correo)).isTrue();
        }

        @Test
        @DisplayName("una fila con un estado que no existe falla al leerse, no se propaga")
        void filaCorrupta() {
            UsuarioEntity corrupta = new UsuarioEntity(usuarioId, "ana.quispe@example.pe", "987654321",
                    "$argon2id$loquesea", "INVENTADO", AHORA, "2026-08", AHORA, AHORA);

            assertThatThrownBy(() -> MapeadorDeUsuario.aDominio(corrupta))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
