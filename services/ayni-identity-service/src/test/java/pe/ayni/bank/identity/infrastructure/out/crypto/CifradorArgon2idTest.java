package pe.ayni.bank.identity.infrastructure.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.ContrasenaCifrada;

/**
 * No levanta Spring: la clase se instancia directamente. Argon2id con los parametros de
 * OWASP tarda del orden de decenas de milisegundos por derivacion, asi que estas pruebas
 * usan las minimas necesarias.
 */
class CifradorArgon2idTest {

    private static final String CONTRASENA = "Cont!rasena2026#";

    private final CifradorArgon2id cifrador = new CifradorArgon2id();

    @Test
    @DisplayName("la derivacion declara ser Argon2id, no otra familia")
    void derivaConArgon2id() {
        ContrasenaCifrada cifrada = cifrador.cifrar(CONTRASENA);

        assertThat(cifrada.valor()).startsWith("$argon2id$");
    }

    @Test
    @DisplayName("la derivacion no contiene la contrasena")
    void laDerivacionNoContieneLaContrasena() {
        assertThat(cifrador.cifrar(CONTRASENA).valor()).doesNotContain(CONTRASENA);
    }

    @Test
    @DisplayName("dos derivaciones de la misma contrasena son distintas: la sal es aleatoria")
    void usaSalAleatoria() {
        // Sin sal aleatoria, dos personas con la misma contrasena tendrian la misma
        // derivacion, y una sola tabla precalculada las rompe a las dos a la vez.
        assertThat(cifrador.cifrar(CONTRASENA).valor())
                .isNotEqualTo(cifrador.cifrar(CONTRASENA).valor());
    }

    @Test
    @DisplayName("verifica correctamente pese a que las sales difieran")
    void verificaLaContrasenaCorrecta() {
        ContrasenaCifrada cifrada = cifrador.cifrar(CONTRASENA);

        assertThat(cifrador.coincide(CONTRASENA, cifrada)).isTrue();
    }

    @Test
    void rechazaUnaContrasenaDistinta() {
        ContrasenaCifrada cifrada = cifrador.cifrar(CONTRASENA);

        assertThat(cifrador.coincide("Otr@Contrasena2026", cifrada)).isFalse();
        assertThat(cifrador.coincide("cont!rasena2026#", cifrada)).isFalse();
    }

    @Test
    @DisplayName("la derivacion lleva dentro los parametros de OWASP")
    void registraLosParametrosDeOwasp() {
        // Van dentro de la propia cadena: es lo que permite subir el coste mas adelante
        // sin invalidar las contrasenas ya guardadas.
        assertThat(cifrador.cifrar(CONTRASENA).valor()).contains("m=19456,t=2,p=1");
    }
}
