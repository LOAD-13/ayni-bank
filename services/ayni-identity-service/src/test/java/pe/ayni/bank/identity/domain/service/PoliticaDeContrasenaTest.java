package pe.ayni.bank.identity.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;

/** Escenario 3 de HU-01: la contrasena no cumple la politica. */
class PoliticaDeContrasenaTest {

    @Test
    @DisplayName("acepta una contrasena que cumple los cinco requisitos")
    void aceptaContrasenaValida() {
        assertThat(PoliticaDeContrasena.evaluar("Cont!rasena2026#")).isEmpty();
        assertThat(PoliticaDeContrasena.cumple("Cont!rasena2026#")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "'Cort4!aA',              LONGITUD_MINIMA",
        "'contrasena2026!',       MAYUSCULA",
        "'CONTRASENA2026!',       MINUSCULA",
        "'Contrasenaqueeslarga!', DIGITO",
        "'Contrasena20261234',    SIMBOLO"
    })
    @DisplayName("senala exactamente el requisito que falta")
    void identificaElRequisitoIncumplido(String contrasena, RequisitoDeContrasena esperado) {
        assertThat(PoliticaDeContrasena.evaluar(contrasena)).containsExactly(esperado);
    }

    @Test
    @DisplayName("devuelve todos los incumplimientos a la vez, no solo el primero")
    void acumulaTodosLosIncumplimientos() {
        // La interfaz los muestra juntos: descubrirlos de uno en uno es como se abandona
        // un formulario de registro.
        assertThat(PoliticaDeContrasena.evaluar("abc"))
                .containsExactlyInAnyOrder(
                        RequisitoDeContrasena.LONGITUD_MINIMA,
                        RequisitoDeContrasena.MAYUSCULA,
                        RequisitoDeContrasena.DIGITO,
                        RequisitoDeContrasena.SIMBOLO);
    }

    @Test
    @DisplayName("una contrasena nula o vacia incumple todos los requisitos")
    void nulaOVaciaIncumpleTodo() {
        assertThat(PoliticaDeContrasena.evaluar(null))
                .containsExactly(RequisitoDeContrasena.values());
        assertThat(PoliticaDeContrasena.evaluar(""))
                .containsExactly(RequisitoDeContrasena.values());
    }

    @Test
    @DisplayName("acepta exactamente doce caracteres: el limite es inclusivo")
    void elLimiteDeLongitudEsInclusivo() {
        assertThat(PoliticaDeContrasena.evaluar("Abcdefghij1!")).isEmpty();
        assertThat(PoliticaDeContrasena.evaluar("Abcdefghi1!"))
                .containsExactly(RequisitoDeContrasena.LONGITUD_MINIMA);
    }

    @Test
    @DisplayName("el espacio no cuenta como simbolo")
    void elEspacioNoEsSimbolo() {
        assertThat(PoliticaDeContrasena.evaluar("Contrasena 2026"))
                .containsExactly(RequisitoDeContrasena.SIMBOLO);
    }

    @Test
    @DisplayName("la lista devuelta es inmutable: nadie la altera despues")
    void laListaDevueltaEsInmutable() {
        var incumplidos = PoliticaDeContrasena.evaluar("abc");

        assertThatThrownBy(() -> incumplidos.add(RequisitoDeContrasena.DIGITO))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("es una clase de utilidad y no se puede instanciar")
    void noSePuedeInstanciar() throws Exception {
        Constructor<PoliticaDeContrasena> constructor =
                PoliticaDeContrasena.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance).hasRootCauseInstanceOf(AssertionError.class);
    }

    @Test
    void cadaRequisitoTieneUnMensajeParaLaPersona() {
        for (RequisitoDeContrasena requisito : RequisitoDeContrasena.values()) {
            assertThat(requisito.mensaje()).isNotBlank();
        }
    }
}
