package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El mismo vector de referencia que OperacionAConfirmarTest de core-banking (ADR-0031). */
class OperacionAConfirmarTest {

    private static final UUID CLAVE = UUID.fromString("7b3e1f2a-9c4d-4e5f-8a6b-1c2d3e4f5a6b");

    /** Calculada aparte con openssl. Compartida con la prueba de core: no cambiar una sin la otra. */
    private static final String HUELLA_DE_REFERENCIA = "q1XjO0hmTgqtuZviV2Hpwi9zA8oSWwlVBYHZdNTmmSw=";

    @Test
    @DisplayName("la huella coincide con la que calcula core-banking")
    void mismoVectorQueCore() {
        OperacionAConfirmar operacion = new OperacionAConfirmar(
                "001-1100000-0-001 ", new BigDecimal("200.5"), "PEN", CLAVE);

        assertThat(operacion.canonica())
                .isEqualTo("TRANSFERENCIA|00111000000001|200.50|PEN|7b3e1f2a-9c4d-4e5f-8a6b-1c2d3e4f5a6b");
        assertThat(operacion.huella()).isEqualTo(HUELLA_DE_REFERENCIA);
    }

    @Test
    @DisplayName("rechaza importes no positivos y monedas mal escritas")
    void validaciones() {
        assertThatThrownBy(() -> new OperacionAConfirmar("1", BigDecimal.ZERO, "PEN", CLAVE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OperacionAConfirmar("1", BigDecimal.ONE, "soles", CLAVE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
