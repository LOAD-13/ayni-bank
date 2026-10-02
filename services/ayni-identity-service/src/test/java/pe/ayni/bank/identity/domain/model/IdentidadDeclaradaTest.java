package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Los datos de identidad que se declaran en el paso 1 del onboarding.
 *
 * <p>Se prueban aparte de {@code ValoresDeIdentidadTest} porque responden a otra pregunta:
 * alli se comprueba que el correo y el celular sean utilizables como credencial; aqui, que
 * lo declarado sirva de termino de comparacion contra lo que lea el OCR en HU-02.
 */
class IdentidadDeclaradaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 30);

    @Nested
    @DisplayName("Tipo de documento")
    class Tipo {

        @ParameterizedTest
        @ValueSource(strings = {"DNI", "dni", " Dni "})
        void aceptaElValorEnCualquierCaja(String entrada) {
            assertThat(TipoDocumento.de(entrada)).isEqualTo(TipoDocumento.DNI);
        }

        @Test
        @DisplayName("el mensaje de error no menciona la clase Java: acaba en la respuesta HTTP")
        void rechazaUnTipoDesconocido() {
            assertThatThrownBy(() -> TipoDocumento.de("LIBRETA"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El tipo de documento debe ser DNI, CE o PASAPORTE.")
                    .hasMessageNotContaining("TipoDocumento");
        }

        @Test
        void rechazaElNulo() {
            assertThatThrownBy(() -> TipoDocumento.de(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Documento de identidad")
    class Documento {

        @Test
        void aceptaUnDniDeOchoDigitos() {
            var documento = new DocumentoDeIdentidad(TipoDocumento.DNI, "45678912");

            assertThat(documento.numero()).isEqualTo("45678912");
            assertThat(documento.ultimos4()).isEqualTo("8912");
        }

        @Test
        @DisplayName("limpia espacios y guiones antes de validar")
        void normalizaLosSeparadores() {
            assertThat(new DocumentoDeIdentidad(TipoDocumento.DNI, " 456-789-12 ").numero())
                    .isEqualTo("45678912");
        }

        @Test
        void pasaAMayusculasElCarneDeExtranjeria() {
            assertThat(new DocumentoDeIdentidad(TipoDocumento.CE, "ab1234567").numero())
                    .isEqualTo("AB1234567");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1234567", "123456789", "4567891a", ""})
        void rechazaLoQueNoEsUnDni(String numero) {
            assertThatThrownBy(() -> new DocumentoDeIdentidad(TipoDocumento.DNI, numero))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaElNulo() {
            assertThatThrownBy(() -> new DocumentoDeIdentidad(TipoDocumento.DNI, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("enmascarado deja solo los cuatro ultimos digitos")
        void enmascara() {
            assertThat(new DocumentoDeIdentidad(TipoDocumento.DNI, "45678912").enmascarado())
                    .isEqualTo("****8912");
        }

        @Test
        @DisplayName("toString no filtra el numero: acaba en cualquier traza de excepcion")
        void noFiltraElNumero() {
            assertThat(new DocumentoDeIdentidad(TipoDocumento.DNI, "45678912").toString())
                    .doesNotContain("45678912")
                    .contains("****8912");
        }
    }

    @Nested
    @DisplayName("Fecha de nacimiento")
    class Nacimiento {

        @Test
        @DisplayName("acepta a quien cumple dieciocho anos justo hoy")
        void aceptaElLimiteExacto() {
            LocalDate cumpleHoy = HOY.minusYears(18);

            assertThat(FechaDeNacimiento.de(cumpleHoy, HOY).edadEn(HOY)).isEqualTo(18);
        }

        @Test
        @DisplayName("rechaza a quien los cumple manana")
        void rechazaUnDiaAntes() {
            LocalDate cumpleManana = HOY.minusYears(18).plusDays(1);

            assertThatThrownBy(() -> FechaDeNacimiento.de(cumpleManana, HOY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("18");
        }

        @Test
        void rechazaUnaFechaFutura() {
            assertThatThrownBy(() -> FechaDeNacimiento.de(HOY.plusDays(1), HOY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("futura");
        }

        @Test
        @DisplayName("rechaza un ano absurdo: casi siempre es un error de tecleo")
        void rechazaUnaEdadImposible() {
            assertThatThrownBy(() -> FechaDeNacimiento.de(LocalDate.of(1850, 1, 1), HOY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("el nulo es IllegalArgumentException, para que salga un 400 y no un 500")
        void rechazaElNulo() {
            assertThatThrownBy(() -> FechaDeNacimiento.de(null, HOY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void dosFechasIgualesSonElMismoValor() {
            var una = FechaDeNacimiento.de(LocalDate.of(1998, 3, 14), HOY);
            var otra = FechaDeNacimiento.de(LocalDate.of(1998, 3, 14), HOY);

            assertThat(una).isEqualTo(otra).hasSameHashCodeAs(otra);
        }

        @Test
        void noFiltraLaFecha() {
            assertThat(FechaDeNacimiento.de(LocalDate.of(1998, 3, 14), HOY).toString())
                    .doesNotContain("1998");
        }
    }

    @Nested
    @DisplayName("Identidad completa")
    class Completa {

        private IdentidadDeclarada con(String nombres, String apellidos) {
            return new IdentidadDeclarada(nombres, apellidos,
                    new DocumentoDeIdentidad(TipoDocumento.DNI, "45678912"),
                    FechaDeNacimiento.de(LocalDate.of(1998, 3, 14), HOY));
        }

        @Test
        @DisplayName("colapsa los espacios interiores, o la comparacion con el OCR fallaria")
        void normalizaLosEspacios() {
            var identidad = con("  Ana   Lucia ", " Quispe  Mendoza ");

            assertThat(identidad.nombres()).isEqualTo("Ana Lucia");
            assertThat(identidad.apellidos()).isEqualTo("Quispe Mendoza");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void rechazaNombresVacios(String nombres) {
            assertThatThrownBy(() -> con(nombres, "Quispe Mendoza"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nombres");
        }

        @Test
        void rechazaApellidosVacios() {
            assertThatThrownBy(() -> con("Ana Lucia", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apellidos");
        }

        @Test
        void rechazaNombresDemasiadoLargos() {
            assertThatThrownBy(() -> con("A".repeat(81), "Quispe"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void exigeDocumentoYFecha() {
            var documento = new DocumentoDeIdentidad(TipoDocumento.DNI, "45678912");
            var fecha = FechaDeNacimiento.de(LocalDate.of(1998, 3, 14), HOY);

            assertThatThrownBy(() -> new IdentidadDeclarada("Ana", "Quispe", null, fecha))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new IdentidadDeclarada("Ana", "Quispe", documento, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toString no filtra ningun dato personal")
        void noFiltraNada() {
            assertThat(con("Ana Lucia", "Quispe Mendoza").toString())
                    .doesNotContain("Ana")
                    .doesNotContain("Quispe")
                    .doesNotContain("45678912");
        }
    }
}
