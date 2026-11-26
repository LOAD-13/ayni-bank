package pe.ayni.bank.core.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.ayni.bank.core.domain.model.Pan;

/**
 * El CVV contra valores calculados aparte (AYNI-304): con la misma clave, el mismo PAN y la
 * misma ventana de cinco minutos, el resultado de un HMAC-SHA256 truncado como RFC 4226 a
 * mano en Python. Si alguien toca un desplazamiento o una máscara del truncado, el CVV deja de
 * coincidir con el de cualquier otra implementación, y las pruebas que solo miran la forma
 * («tres dígitos, estable en la ventana») no lo notarían.
 */
class CvvDeReferenciaTest {

    /** Los bytes 0 a 31, que es la clave de los valores de referencia. */
    private static final byte[] CLAVE = new byte[32];

    static {
        for (int i = 0; i < CLAVE.length; i++) {
            CLAVE[i] = (byte) i;
        }
    }

    @ParameterizedTest(name = "{0} en {1} s da {2}")
    @CsvSource({
        "4999990000000007, 1800000000, 188",
        "4999990000000007, 1800000299, 188",
        "4999990000000007, 1800000300, 055",
        "4539578763621486, 1800000000, 172",
        "4539578763621486, 1800123456, 853",
        "4999990000000007, 1700000000, 682",
        "4539578763621486, 1900000000, 513",
        "4999990000000007, 1234567890, 862",
        "4539578763621486, 1500000000, 154",
        "4999990000000007, 1600000000, 809",
    })
    void coincideConElValorDeReferencia(String pan, long segundos, String esperado) {
        assertThat(new CalculadoraDeCvv(CLAVE).calcular(new Pan(pan), Instant.ofEpochSecond(segundos)))
                .isEqualTo(esperado);
    }
}
