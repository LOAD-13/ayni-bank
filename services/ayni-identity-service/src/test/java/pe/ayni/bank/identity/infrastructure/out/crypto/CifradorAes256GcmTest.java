package pe.ayni.bank.identity.infrastructure.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cifrado en reposo del numero de documento. */
class CifradorAes256GcmTest {

    private static final String CLAVE =
            Base64.getEncoder().encodeToString("ayni-clave-de-pruebas-32-bytes!!".getBytes());

    private final CifradorAes256Gcm cifrador = new CifradorAes256Gcm(CLAVE);

    @Test
    void loQueSeCifraSeRecupera() {
        assertThat(cifrador.descifrar(cifrador.cifrar("45678912"))).isEqualTo("45678912");
    }

    @Test
    @DisplayName("el criptograma no contiene el dato en claro")
    void noDejaElDatoALaVista() {
        assertThat(cifrador.cifrar("45678912")).doesNotContain("45678912");
    }

    @Test
    @DisplayName("cifrar dos veces el mismo dato da resultados distintos: el IV es aleatorio")
    void noEsDeterminista() {
        // Esta es la razon por la que la columna cifrada no sirve para buscar ni para
        // imponer unicidad, y por la que existe la columna de los ultimos cuatro digitos.
        assertThat(cifrador.cifrar("45678912")).isNotEqualTo(cifrador.cifrar("45678912"));
    }

    @Test
    void declaraElEsquemaParaPoderRotarLaClaveDespues() {
        assertThat(cifrador.cifrar("45678912")).startsWith("v1:");
    }

    @Test
    @DisplayName("un criptograma manipulado falla en lugar de devolver basura")
    void detectaLaManipulacion() {
        String criptograma = cifrador.cifrar("45678912");
        // Se altera el ultimo caracter del Base64. GCM autentica: la etiqueta deja de
        // cuadrar y el descifrado se niega. Con CBC esto devolveria bytes cualesquiera.
        char ultimo = criptograma.charAt(criptograma.length() - 2);
        String alterado = criptograma.substring(0, criptograma.length() - 2)
                + (ultimo == 'A' ? 'B' : 'A')
                + criptograma.charAt(criptograma.length() - 1);

        assertThatThrownBy(() -> cifrador.descifrar(alterado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaUnCriptogramaSinEsquemaConocido() {
        assertThatThrownBy(() -> cifrador.descifrar("v9:loquesea"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("una clave del tamano equivocado impide arrancar, no falla en el primer alta")
    void exigeUnaClaveDe256Bits() {
        String corta = Base64.getEncoder().encodeToString("demasiado-corta".getBytes());

        assertThatThrownBy(() -> new CifradorAes256Gcm(corta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void elNuloAtraviesaSinRomper() {
        assertThat(cifrador.cifrar(null)).isNull();
        assertThat(cifrador.descifrar(null)).isNull();
    }
}
