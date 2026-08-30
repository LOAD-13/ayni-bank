package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El invariante del que dependen todos los demás: los importes son exactos. */
class DineroTest {

    @Test
    @DisplayName("diez veces diez céntimos son exactamente un sol")
    void laSumaEsExacta() {
        // Con `double` esto da 0.9999999999999999. No es una curiosidad académica: es la
        // razón por la que los importes de este sistema no pueden ser de coma flotante.
        Dinero total = Dinero.cero(Moneda.PEN);
        for (int i = 0; i < 10; i++) {
            total = total.mas(Dinero.de("0.10", Moneda.PEN));
        }

        assertThat(total).isEqualTo(Dinero.de("1.00", Moneda.PEN));
        assertThat(total.importe()).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("redondea con HALF_EVEN, no con HALF_UP")
    void usaElRedondeoDelBanquero() {
        // HALF_UP empujaría los dos hacia arriba (0.13 y 0.15) e introduciría un sesgo
        // acumulado a favor de una de las partes. HALF_EVEN alterna: el sesgo tiende a cero.
        assertThat(Dinero.de("0.125", Moneda.PEN).importe())
                .isEqualByComparingTo(new BigDecimal("0.12"));
        assertThat(Dinero.de("0.135", Moneda.PEN).importe())
                .isEqualByComparingTo(new BigDecimal("0.14"));
    }

    @Test
    @DisplayName("normaliza la escala: 10 y 10.00 son el mismo importe")
    void normalizaLaEscala() {
        assertThat(Dinero.de("10", Moneda.PEN)).isEqualTo(Dinero.de("10.00", Moneda.PEN));
    }

    @Test
    void noSumaMonedasDistintas() {
        assertThatThrownBy(() -> Dinero.de("10.00", Moneda.PEN)
                .mas(Dinero.de("10.00", Moneda.USD)))
                .isInstanceOf(MonedasIncompatiblesException.class)
                .hasMessageContaining("PEN")
                .hasMessageContaining("USD");
    }

    @Test
    void restaYSigno() {
        Dinero resultado = Dinero.de("10.00", Moneda.PEN).menos(Dinero.de("25.50", Moneda.PEN));

        assertThat(resultado.esNegativo()).isTrue();
        assertThat(resultado.importe()).isEqualByComparingTo(new BigDecimal("-15.50"));
    }

    @Test
    void ceroEsCero() {
        assertThat(Dinero.cero(Moneda.PEN).esCero()).isTrue();
        assertThat(Dinero.cero(Moneda.PEN).esPositivo()).isFalse();
        assertThat(Dinero.cero(Moneda.PEN).esNegativo()).isFalse();
    }

    @Test
    void seMuestraConSuSimbolo() {
        assertThat(Dinero.de("12480.65", Moneda.PEN).formateado()).isEqualTo("S/ 12480.65");
        assertThat(Dinero.de("50.00", Moneda.USD).formateado()).isEqualTo("US$ 50.00");
    }

    @Test
    void exigeImporteYMoneda() {
        assertThatThrownBy(() -> new Dinero(null, Moneda.PEN))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Dinero(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }
}
