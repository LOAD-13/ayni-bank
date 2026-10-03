package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** HU-02: los datos del DNI y su contraste con lo declarado (ADR-0009). */
class DatosDelDniTest {

    private static final LocalDate NACIMIENTO = LocalDate.of(1990, 5, 15);
    private static final LocalDate EMISION = LocalDate.of(2021, 8, 20);

    private static DatosDelDni ana() {
        return new DatosDelDni("44556677", "ANA LUCIA", "QUISPE MAMANI", NACIMIENTO, "F", EMISION);
    }

    private static DatosDeclarados declarados(String nombres, String apellidos, TipoDocumento tipo, String numero) {
        return new DatosDeclarados(nombres, apellidos, tipo, numero, NACIMIENTO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4455667", "445566778", "A4556677", "4455 677", ""})
    @DisplayName("el numero son exactamente ocho digitos")
    void rechazaNumerosQueNoSonOchoDigitos(String numero) {
        assertThatThrownBy(() -> new DatosDelDni(numero, "ANA", "QUISPE", NACIMIENTO, "F", EMISION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el sexo es M o F, y la fecha de emision no puede ser anterior al nacimiento")
    void validaSexoYFechas() {
        assertThatThrownBy(() -> new DatosDelDni("44556677", "ANA", "QUISPE", NACIMIENTO, "<", EMISION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatosDelDni("44556677", "ANA", "QUISPE", NACIMIENTO, "F",
                NACIMIENTO.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DatosDelDni("44556677", "ANA", "QUISPE", NACIMIENTO, "f", null).sexo()).isEqualTo("F");
    }

    @Test
    @DisplayName("coincide con lo declarado sin distinguir mayusculas, tildes ni espacios de mas")
    void coincideSinDistinguirFormato() {
        assertThat(ana().coincideCon(declarados("Ana  Lucía", " Quispe Mamani", TipoDocumento.DNI, "44556677")))
                .isTrue();
    }

    @Test
    @DisplayName("coincide si se declaro solo parte del nombre que figura en el DNI")
    void coincideConParteDelNombre() {
        assertThat(ana().coincideCon(declarados("Ana", "Quispe Mamani", TipoDocumento.DNI, "44556677")))
                .isTrue();
        assertThat(ana().coincideCon(declarados("Lucia", "Quispe", TipoDocumento.DNI, "44556677")))
                .isTrue();
    }

    @Test
    @DisplayName("no coincide si cambia el numero, el nombre, la fecha o el tipo de documento declarado")
    void noCoincideSiCambiaAlgo() {
        assertThat(ana().coincideCon(declarados("Ana Lucia", "Quispe Mamani", TipoDocumento.DNI, "44556678")))
                .isFalse();
        assertThat(ana().coincideCon(declarados("Ana Maria", "Quispe Mamani", TipoDocumento.DNI, "44556677")))
                .isFalse();
        assertThat(ana().coincideCon(declarados("Ana Lucia", "Quispe Flores", TipoDocumento.DNI, "44556677")))
                .isFalse();
        assertThat(ana().coincideCon(declarados("Ana Lucia", "Quispe Mamani", TipoDocumento.CE, "44556677")))
                .isFalse();
        assertThat(ana().coincideCon(new DatosDeclarados("Ana Lucia", "Quispe Mamani", TipoDocumento.DNI,
                "44556677", NACIMIENTO.plusDays(1)))).isFalse();
    }

    @Test
    @DisplayName("contradice al MRZ solo si cambia el numero o la fecha de nacimiento")
    void contradiceAlMrzSoloEnLosCamposVerificados() {
        DatosDelDni conOtroNombre = new DatosDelDni("44556677", "ANA", "QUISPE", NACIMIENTO, "F", EMISION);
        DatosDelDni conOtroNumero = new DatosDelDni("44556678", "ANA LUCIA", "QUISPE MAMANI", NACIMIENTO, "F", EMISION);

        assertThat(conOtroNombre.contradiceLoVerificadoEn(ana())).isFalse();
        assertThat(conOtroNumero.contradiceLoVerificadoEn(ana())).isTrue();
    }

    @Test
    @DisplayName("no deja rastro en los logs: solo los cuatro ultimos digitos")
    void noDejaRastro() {
        assertThat(ana().toString()).doesNotContain("QUISPE").doesNotContain("44556677").contains("****6677");
        assertThat(ana().numeroEnmascarado()).isEqualTo("****6677");
    }

    @Test
    @DisplayName("confirmar sin reescribir el numero conserva el que se leyo")
    void confirmarConservaElNumeroLeido() {
        DatosConfirmados confirmados = new DatosConfirmados("", "Ana Lucia", "Quispe Mamani", NACIMIENTO, "F",
                EMISION);

        assertThat(confirmados.completarCon(ana()).numero()).isEqualTo("44556677");
        assertThat(confirmados.completarCon(ana()).difiereDe(ana())).isFalse();
    }
}
