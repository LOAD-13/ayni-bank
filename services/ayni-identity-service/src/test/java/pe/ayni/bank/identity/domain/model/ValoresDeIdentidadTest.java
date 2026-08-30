package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Objetos de valor y errores del agregado de identidad. */
class ValoresDeIdentidadTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final String HASH = "$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHQ$abcdefgh";

    @Nested
    @DisplayName("ContrasenaCifrada")
    class Cifrada {

        @Test
        void aceptaUnaDerivacionDeArgon2() {
            assertThat(new ContrasenaCifrada(HASH).valor()).isEqualTo(HASH);
        }

        @Test
        @DisplayName("rechaza lo que no sea una derivacion de Argon2: ahi se detecta el fallo grave")
        void rechazaLoQueNoEsArgon2() {
            // Si esto pasara, se estaria guardando la contrasena en claro o con un hash
            // que no es el acordado, y nadie se enteraria hasta una auditoria.
            assertThatThrownBy(() -> new ContrasenaCifrada("Cont!rasena2026#"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Argon2id");
            assertThatThrownBy(() -> new ContrasenaCifrada("$2a$10$bcryptbcryptbcrypt"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaVaciaYNula() {
            assertThatThrownBy(() -> new ContrasenaCifrada("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ContrasenaCifrada(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toString oculta la derivacion, que es material para un ataque fuera de linea")
        void elToStringNoExponeLaDerivacion() {
            assertThat(new ContrasenaCifrada(HASH)).hasToString("ContrasenaCifrada[oculta]");
        }
    }

    @Nested
    @DisplayName("Consentimiento")
    class ConsentimientoDeDatos {

        @Test
        @DisplayName("guarda el momento, que es lo que exige demostrar la Ley N.o 29733")
        void registraElMomento() {
            Consentimiento consentimiento = Consentimiento.otorgar(true, AHORA, "v1");

            assertThat(consentimiento.otorgadoEn()).isEqualTo(AHORA);
            assertThat(consentimiento.versionDeLosTerminos()).isEqualTo("v1");
        }

        @Test
        @DisplayName("no acepta significa no hay registro: escenario 4 de HU-01")
        void sinAceptacionNoHayConsentimiento() {
            assertThatThrownBy(() -> Consentimiento.otorgar(false, AHORA, "v1"))
                    .isInstanceOf(ConsentimientoNoOtorgadoException.class)
                    .hasMessageContaining("aceptar los terminos");
        }

        @Test
        void exigeMomentoYVersion() {
            assertThatThrownBy(() -> new Consentimiento(null, "v1"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Consentimiento(AHORA, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Consentimiento(AHORA, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("EstadoUsuario")
    class Estados {

        @Test
        @DisplayName("solo ACTIVO puede operar")
        void soloActivoOpera() {
            assertThat(EstadoUsuario.ACTIVO.puedeOperar()).isTrue();
            assertThat(EstadoUsuario.PENDIENTE_VERIFICACION.puedeOperar()).isFalse();
            assertThat(EstadoUsuario.EN_REVISION.puedeOperar()).isFalse();
            assertThat(EstadoUsuario.BLOQUEADO.puedeOperar()).isFalse();
        }
    }

    @Nested
    @DisplayName("ContrasenaInvalidaException")
    class ContrasenaInvalida {

        @Test
        void enumeraLosRequisitosIncumplidosSinRevelarLaContrasena() {
            var excepcion = new ContrasenaInvalidaException(
                    List.of(RequisitoDeContrasena.DIGITO, RequisitoDeContrasena.SIMBOLO));

            assertThat(excepcion.incumplidos())
                    .containsExactly(RequisitoDeContrasena.DIGITO, RequisitoDeContrasena.SIMBOLO);
            assertThat(excepcion.getMessage())
                    .contains("al menos un digito")
                    .contains("al menos un simbolo");
        }

        @Test
        @DisplayName("no tiene sentido lanzarla sin incumplimientos")
        void exigeAlMenosUnIncumplimiento() {
            assertThatThrownBy(() -> new ContrasenaInvalidaException(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ContrasenaInvalidaException(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("ComandoDeRegistro y ResultadoDeRegistro")
    class ComandoYResultado {

        @Test
        @DisplayName("el comando lleva la contrasena en claro y su toString no la imprime")
        void elComandoNoFiltraLaContrasena() {
            var comando = new ComandoDeRegistro(
                    "ana@example.pe", "987654321", "Cont!rasena2026#", true);

            assertThat(comando.toString())
                    .doesNotContain("Cont!rasena2026#")
                    .doesNotContain("ana@example.pe")
                    .doesNotContain("987654321")
                    .contains("aceptaTerminos=true");
        }

        @Test
        void elComandoExigeSusTresCampos() {
            assertThatThrownBy(
                    () -> new ComandoDeRegistro(null, "987654321", "clave", true))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                    () -> new ComandoDeRegistro("ana@example.pe", null, "clave", true))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                    () -> new ComandoDeRegistro("ana@example.pe", "987654321", null, true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("el resultado usa el mensaje neutro que no revela si el correo existe")
        void elResultadoEsNeutro() {
            UUID solicitud = UUID.randomUUID();

            var resultado = ResultadoDeRegistro.aceptado(solicitud);

            assertThat(resultado.solicitudId()).isEqualTo(solicitud);
            assertThat(resultado.estado()).isEqualTo(EstadoUsuario.PENDIENTE_VERIFICACION);
            assertThat(resultado.mensaje()).isEqualTo(ResultadoDeRegistro.MENSAJE_NEUTRO);
            assertThat(resultado.mensaje()).contains("Si el correo esta disponible");
        }

        @Test
        void elResultadoExigeSusTresCampos() {
            UUID id = UUID.randomUUID();

            assertThatThrownBy(() -> new ResultadoDeRegistro(
                    null, EstadoUsuario.PENDIENTE_VERIFICACION, "m"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ResultadoDeRegistro(id, null, "m"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ResultadoDeRegistro(
                    id, EstadoUsuario.PENDIENTE_VERIFICACION, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
