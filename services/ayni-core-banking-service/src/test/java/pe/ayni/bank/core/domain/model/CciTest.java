package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** El CCI de veinte digitos y sus dos digitos de control · HU-05 / HU-11. */
class CciTest {

    /** CCI ya usado como dato fijo en otras pruebas del proyecto. */
    private static final String VALIDO = "99900101000000000103";

    /** Cambia un digito de la cadena por otro. */
    private static String conDigito(String cci, int posicion, char nuevo) {
        char[] c = cci.toCharArray();
        c[posicion] = nuevo;
        return new String(c);
    }

    /** Intercambia dos digitos contiguos. */
    private static String intercambiando(String cci, int posicion) {
        char[] c = cci.toCharArray();
        char tmp = c[posicion];
        c[posicion] = c[posicion + 1];
        c[posicion + 1] = tmp;
        return new String(c);
    }

    @Nested
    @DisplayName("Construccion")
    class Construccion {

        @Test
        void aceptaUnCciConSuControlCorrecto() {
            Cci cci = new Cci(VALIDO);
            assertThat(cci.valor()).isEqualTo(VALIDO);
            assertThat(cci.banco()).isEqualTo("999");
            assertThat(cci.oficina()).isEqualTo("001");
            assertThat(cci.ultimos4()).isEqualTo("0103");
        }

        @Test
        void rechazaUnCciConElControlEquivocado() {
            String conControlMalo = VALIDO.substring(0, 18) + "00";
            assertThatThrownBy(() -> new Cci(conControlMalo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comprobacion de control");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {
            "9990010100000000010", 
            "999001010000000001030", 
            "9990010100000000010A", 
            "99900101 0000000001 3", 
            "٩٩٩٠٠١٠١٠٠٠٠٠٠٠٠٠١٠٣" 
        })
        void rechazaLoQueNoSonVeinteDigitosAscii(String texto) {
            assertThatThrownBy(() -> new Cci(texto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("20 digitos");
        }
    }

    @Nested
    @DisplayName("Digito de control")
    class DigitoDeControl {

        @Test
        void cuadraParaLosCcisQueGeneraElPropioBanco() {
            // Si el control calculado al generar no coincidiera con el comprobado al
            // reconstruir, el sistema no podria leer de la base lo que el mismo escribio.
            for (long correlativo : new long[] {0, 1, 7, 99, 123_456, 9_999_999_999L}) {
                NumeroDeCuenta numero = NumeroDeCuenta.de(Moneda.PEN, correlativo);
                Cci cci = Cci.para(numero);
                assertThat(Cci.esValido(cci.valor())).isTrue();
                assertThat(new Cci(cci.valor())).isEqualTo(cci);
            }
        }

        @Test
        void detectaCualquierErrorDeUnSoloDigito() {
            // Exhaustivo: cada posicion de los 18 digitos de cuerpo, cada uno de los 9
            // valores distintos. Con pesos 2..9 y modulo 97, ninguno puede pasar.
            for (int pos = 0; pos < 18; pos++) {
                for (char d = '0'; d <= '9'; d++) {
                    if (d == VALIDO.charAt(pos)) {
                        continue;
                    }
                    assertThat(Cci.esValido(conDigito(VALIDO, pos, d)))
                            .as("posicion %d cambiada a %c", pos, d)
                            .isFalse();
                }
            }
        }

        @Test
        void detectaElErrorEnLosPropiosDigitosDeControl() {
            for (int pos = 18; pos < 20; pos++) {
                for (char d = '0'; d <= '9'; d++) {
                    if (d == VALIDO.charAt(pos)) {
                        continue;
                    }
                    assertThat(Cci.esValido(conDigito(VALIDO, pos, d))).isFalse();
                }
            }
        }

        @Test
        void detectaDosDigitosContiguosIntercambiados() {
            // El error de tecleo mas comun despues del digito cambiado. Solo se prueban
            // las parejas que son realmente distintas: intercambiar dos iguales no cambia
            // nada y no es un error.
            for (int pos = 0; pos < 17; pos++) {
                if (VALIDO.charAt(pos) == VALIDO.charAt(pos + 1)) {
                    continue;
                }
                assertThat(Cci.esValido(intercambiando(VALIDO, pos)))
                        .as("intercambio en la posicion %d", pos)
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("esValido y desde: lo que escribe el cliente")
    class LoQueEscribeElCliente {

        @Test
        void aceptaElCciValido() {
            assertThat(Cci.esValido(VALIDO)).isTrue();
        }

        @Test
        void toleraLaAgrupacionConGuionesYEspacios() {
            assertThat(Cci.esValido("999-001-0100-0000-0001-03")).isTrue();
            assertThat(Cci.esValido(" 999 001 0100 0000 0001 03 ")).isTrue();
            assertThat(Cci.desde("999-001-0100-0000-0001-03").valor()).isEqualTo(VALIDO);
        }

        @Test
        void aceptaElTextoQueMuestraElPropioCci() {
            Cci cci = new Cci(VALIDO);
            assertThat(Cci.desde(cci.formateado())).isEqualTo(cci);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"abc", "9990010100000000010", "99900101000000000100", "999.001.0100"})
        void rechazaLoInvalidoSinLanzarExcepcion(String texto) {
            assertThat(Cci.esValido(texto)).isFalse();
        }

        @Test
        void desdeLanzaExcepcionSiNoEsValido() {
            assertThatThrownBy(() -> Cci.desde("99900101000000000100"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Cci.desde(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void formateaDeTresEnTres() {
        assertThat(new Cci(VALIDO).formateado()).isEqualTo("999-001-0100-0000-0001-03");
    }
}