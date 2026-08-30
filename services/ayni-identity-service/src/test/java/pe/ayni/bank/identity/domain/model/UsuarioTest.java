package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHQ$abcdefgh";

    private static Usuario unUsuarioRecienRegistrado() {
        return Usuario.registrar(
                UUID.randomUUID(),
                new CorreoElectronico("ana.quispe@example.pe"),
                new Celular("987654321"),
                new ContrasenaCifrada(HASH),
                Consentimiento.otorgar(true, AHORA, "v1"),
                AHORA);
    }

    @Nested
    @DisplayName("Registro")
    class Registro {

        @Test
        @DisplayName("un usuario recien registrado queda PENDIENTE_VERIFICACION")
        void quedaPendienteDeVerificacion() {
            assertThat(unUsuarioRecienRegistrado().estado())
                    .isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
        }

        @Test
        @DisplayName("un usuario recien registrado no puede operar: existir no es poder mover dinero")
        void noPuedeOperarAntesDelKyc() {
            assertThat(unUsuarioRecienRegistrado().puedeOperar()).isFalse();
        }

        @Test
        void conservaLosDatosConLosQueSeRegistro() {
            Usuario usuario = unUsuarioRecienRegistrado();

            assertThat(usuario.correo().valor()).isEqualTo("ana.quispe@example.pe");
            assertThat(usuario.celular().valor()).isEqualTo("987654321");
            assertThat(usuario.registradoEn()).isEqualTo(AHORA);
            assertThat(usuario.consentimiento().otorgadoEn()).isEqualTo(AHORA);
            assertThat(usuario.contrasena().valor()).isEqualTo(HASH);
        }
    }

    @Nested
    @DisplayName("Transiciones de estado")
    class Transiciones {

        @Test
        void activarLoDejaOperativo() {
            Usuario activo = unUsuarioRecienRegistrado().activar();

            assertThat(activo.estado()).isEqualTo(EstadoUsuario.ACTIVO);
            assertThat(activo.puedeOperar()).isTrue();
        }

        @Test
        @DisplayName("un usuario en revision manual puede acabar activandose")
        void desdeRevisionSePuedeActivar() {
            Usuario usuario = unUsuarioRecienRegistrado().derivarARevision();

            assertThat(usuario.estado()).isEqualTo(EstadoUsuario.EN_REVISION);
            assertThat(usuario.puedeOperar()).isFalse();
            assertThat(usuario.activar().estado()).isEqualTo(EstadoUsuario.ACTIVO);
        }

        @Test
        @DisplayName("no se puede reactivar a un bloqueado: seria desbloquear sin pasar por nadie")
        void noSePuedeActivarUnBloqueado() {
            Usuario bloqueado = unUsuarioRecienRegistrado().bloquear();

            assertThatThrownBy(bloqueado::activar)
                    .isInstanceOf(TransicionDeEstadoInvalidaException.class)
                    .hasMessageContaining("BLOQUEADO");
        }

        @Test
        void noSePuedeDerivarARevisionAQuienYaEstaActivo() {
            Usuario activo = unUsuarioRecienRegistrado().activar();

            assertThatThrownBy(activo::derivarARevision)
                    .isInstanceOf(TransicionDeEstadoInvalidaException.class);
        }

        @Test
        @DisplayName("la transicion devuelve una instancia nueva y no muta la anterior")
        void esInmutable() {
            Usuario original = unUsuarioRecienRegistrado();

            Usuario activo = original.activar();

            assertThat(original.estado()).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
            assertThat(activo).isNotSameAs(original);
        }

        @Test
        void laExcepcionDiceDeDondeAdondeSeIntentoIr() {
            Usuario bloqueado = unUsuarioRecienRegistrado().bloquear();

            assertThatThrownBy(bloqueado::activar)
                    .isInstanceOfSatisfying(TransicionDeEstadoInvalidaException.class, e -> {
                        assertThat(e.origen()).isEqualTo(EstadoUsuario.BLOQUEADO);
                        assertThat(e.destino()).isEqualTo(EstadoUsuario.ACTIVO);
                    });
        }
    }

    @Nested
    @DisplayName("Identidad")
    class Identidad {

        @Test
        @DisplayName("dos instancias con el mismo id son la misma persona aunque difiera el estado")
        void laIdentidadEsElId() {
            Usuario registrado = unUsuarioRecienRegistrado();
            Usuario activo = registrado.activar();

            assertThat(registrado).isEqualTo(activo);
            assertThat(registrado).hasSameHashCodeAs(activo);
        }

        @Test
        void usuariosConIdDistintoNoSonIguales() {
            assertThat(unUsuarioRecienRegistrado()).isNotEqualTo(unUsuarioRecienRegistrado());
        }

        @Test
        void noEsIgualANadaQueNoSeaUnUsuario() {
            Usuario usuario = unUsuarioRecienRegistrado();

            // `equals` se invoca directamente y no a traves de `isNotEqualTo`: comparar un
            // Usuario con un String desde el aserto es comparar tipos incompatibles, cosa
            // que casi siempre delata un error de la prueba y no una intencion.
            assertThat(usuario.equals("no soy un usuario")).isFalse();
            assertThat(usuario.equals(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Fugas de datos personales")
    class Fugas {

        @Test
        @DisplayName("toString no expone correo, celular ni derivacion de la contrasena")
        void elToStringNoFiltraNada() {
            // Este toString acaba en cualquier traza de excepcion que lleve un usuario
            // dentro. Lo que no aparezca aqui no puede filtrarse por esa via.
            String texto = unUsuarioRecienRegistrado().toString();

            assertThat(texto)
                    .doesNotContain("ana.quispe@example.pe")
                    .doesNotContain("987654321")
                    .doesNotContain(HASH)
                    .contains("estado=PENDIENTE_VERIFICACION");
        }
    }

    @Nested
    @DisplayName("Reconstitucion desde persistencia")
    class Reconstitucion {

        @Test
        @DisplayName("admite cualquier estado, porque la base de datos ya tiene usuarios ACTIVOS")
        void admiteCualquierEstado() {
            UUID id = UUID.randomUUID();

            Usuario usuario = Usuario.reconstituir(
                    id,
                    new CorreoElectronico("ana@example.pe"),
                    new Celular("987654321"),
                    new ContrasenaCifrada(HASH),
                    EstadoUsuario.ACTIVO,
                    new Consentimiento(AHORA, "v1"),
                    AHORA);

            assertThat(usuario.id()).isEqualTo(id);
            assertThat(usuario.estado()).isEqualTo(EstadoUsuario.ACTIVO);
        }
    }
}
