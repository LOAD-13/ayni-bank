package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas de dominio puro: ni Spring, ni base de datos, ni contexto que levantar. */
class CorreoElectronicoTest {

    @Nested
    @DisplayName("Normalizacion")
    class Normalizacion {

        @Test
        @DisplayName("pasa el correo a minusculas para que la unicidad signifique algo")
        void normalizaAMinusculas() {
            assertThat(new CorreoElectronico("Ana.Quispe@Example.PE").valor())
                    .isEqualTo("ana.quispe@example.pe");
        }

        @Test
        @DisplayName("elimina los espacios que la gente pega sin darse cuenta")
        void recortaEspacios() {
            assertThat(new CorreoElectronico("  ana@example.pe  ").valor())
                    .isEqualTo("ana@example.pe");
        }

        @Test
        @DisplayName("dos correos que solo difieren en mayusculas son el mismo")
        void mismaIdentidadIndependientementeDeLasMayusculas() {
            assertThat(new CorreoElectronico("ANA@EXAMPLE.PE"))
                    .isEqualTo(new CorreoElectronico("ana@example.pe"));
        }
    }

    @Nested
    @DisplayName("Validacion")
    class Validacion {

        @ParameterizedTest
        @ValueSource(strings = {
            "ana@example.pe",
            "ana.lucia@example.com.pe",
            "ana+banco@example.pe",
            "ana_quispe@sub.example.pe",
            "a@b.pe"
        })
        void aceptaDireccionesValidas(String correo) {
            assertThatNoException().isThrownBy(() -> new CorreoElectronico(correo));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "sin-arroba.pe",
            "@example.pe",
            "ana@",
            "ana@example",
            "ana@.pe",
            "ana quispe@example.pe",
            ""
        })
        void rechazaDireccionesInvalidas(String correo) {
            assertThatThrownBy(() -> new CorreoElectronico(correo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("formato valido");
        }

        @Test
        void rechazaNulo() {
            assertThatThrownBy(() -> new CorreoElectronico(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rechaza direcciones que superan el limite del RFC 5321")
        void rechazaDireccionesDemasiadoLargas() {
            String larguisimo = "a".repeat(250) + "@example.pe";

            assertThatThrownBy(() -> new CorreoElectronico(larguisimo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("longitud maxima");
        }
    }

    @Nested
    @DisplayName("Enmascarado para diagnostico")
    class Enmascarado {

        @Test
        void ocultaElInteriorDeLaParteLocal() {
            assertThat(new CorreoElectronico("anaquispe@example.pe").enmascarado())
                    .isEqualTo("a***e@example.pe");
        }

        @Test
        @DisplayName("oculta la parte local entera cuando es demasiado corta para revelar nada")
        void ocultaLaParteLocalCortaPorCompleto() {
            assertThat(new CorreoElectronico("ab@example.pe").enmascarado())
                    .isEqualTo("***@example.pe");
        }

        @Test
        @DisplayName("la mascara mide siempre lo mismo: la longitud tambien es informacion")
        void laMascaraNoRevelaLaLongitud() {
            // Un asterisco por caracter oculto delata cuantos hay, y saber que una parte
            // local mide diecisiete caracteres ayuda a quien intente adivinarla.
            String corto = new CorreoElectronico("ana@example.pe").enmascarado();
            String largo = new CorreoElectronico("anaquispebeatriz@example.pe").enmascarado();

            assertThat(corto).isEqualTo("a***a@example.pe");
            assertThat(largo).isEqualTo("a***z@example.pe").hasSameSizeAs(corto);
        }

        @Test
        void elEnmascaradoNuncaContieneLaParteLocalCompleta() {
            assertThat(new CorreoElectronico("anaquispe@example.pe").enmascarado())
                    .doesNotContain("anaquispe");
        }
    }
}
