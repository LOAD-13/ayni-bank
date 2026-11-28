package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TipoDeCambioOficialTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    @Test
    void acepta_compra_y_venta_positivas_con_la_venta_igual_o_mayor() {
        assertThatCode(() -> new TipoDeCambioOficial(HOY, new BigDecimal("3.401"), new BigDecimal("3.407"), "BCRP"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new TipoDeCambioOficial(HOY, new BigDecimal("3.4"), new BigDecimal("3.4"), "BCRP"))
                .doesNotThrowAnyException();
    }

    @Test
    void rechaza_un_tipo_cero_o_negativo() {
        assertThatThrownBy(() -> new TipoDeCambioOficial(HOY, BigDecimal.ZERO, new BigDecimal("3.4"), "BCRP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TipoDeCambioOficial(HOY, new BigDecimal("3.4"), new BigDecimal("-1"), "BCRP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechaza_una_venta_menor_que_la_compra() {
        assertThatThrownBy(() -> new TipoDeCambioOficial(HOY, new BigDecimal("3.5"), new BigDecimal("3.4"), "BCRP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("venta");
    }

    @Test
    void exige_todos_los_datos() {
        assertThatThrownBy(() -> new TipoDeCambioOficial(null, BigDecimal.ONE, BigDecimal.ONE, "BCRP"))
                .isInstanceOf(NullPointerException.class);
    }
}
