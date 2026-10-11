package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La huella de la operacion la calculan dos servicios por separado: identity al emitir el
 * token de confirmacion y core al comprobarlo. Si un dia divergen, ninguna transferencia
 * pasa. Por eso las dos suites comprueban <strong>el mismo vector de referencia</strong>:
 * cualquier cambio en un lado sin el otro rompe la CI. Ver ADR-0031.
 */
class OperacionAConfirmarTest {

    private static final UUID CLAVE = UUID.fromString("7b3e1f2a-9c4d-4e5f-8a6b-1c2d3e4f5a6b");

    /** Calculada aparte con openssl. Compartida con la prueba de identity: no cambiar una sin la otra. */
    static final String HUELLA_DE_REFERENCIA = "q1XjO0hmTgqtuZviV2Hpwi9zA8oSWwlVBYHZdNTmmSw=";

    @Test
    @DisplayName("la forma canonica normaliza el destino y el importe")
    void canonica() {
        OperacionAConfirmar operacion = new OperacionAConfirmar(
                "001-1100000-0-001 ", Dinero.de("200.5", Moneda.PEN), CLAVE);

        assertThat(operacion.canonica())
                .isEqualTo("TRANSFERENCIA|00111000000001|200.50|PEN|7b3e1f2a-9c4d-4e5f-8a6b-1c2d3e4f5a6b");
    }

    @Test
    @DisplayName("la huella es el SHA-256 en Base64 de la forma canonica")
    void huella() {
        OperacionAConfirmar operacion = new OperacionAConfirmar(
                "00111000000001", Dinero.de("200.50", Moneda.PEN), CLAVE);

        assertThat(operacion.huella()).isEqualTo(HUELLA_DE_REFERENCIA).hasSize(44);
    }

    @Test
    @DisplayName("cambiar el destino, el importe o la clave cambia la huella")
    void distintas() {
        String base = new OperacionAConfirmar("00111000000001", Dinero.de("200.50", Moneda.PEN), CLAVE).huella();

        assertThat(new OperacionAConfirmar("00111000000002", Dinero.de("200.50", Moneda.PEN), CLAVE).huella())
                .isNotEqualTo(base);
        assertThat(new OperacionAConfirmar("00111000000001", Dinero.de("200.51", Moneda.PEN), CLAVE).huella())
                .isNotEqualTo(base);
        assertThat(new OperacionAConfirmar("00111000000001", Dinero.de("200.50", Moneda.PEN), UUID.randomUUID())
                .huella()).isNotEqualTo(base);
    }
}
