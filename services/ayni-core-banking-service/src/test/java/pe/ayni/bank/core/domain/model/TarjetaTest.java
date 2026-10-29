package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.service.CalculadoraDeCvv;
import pe.ayni.bank.core.domain.service.GeneradorDePan;

/** HU-09 en el dominio, sin Spring: PAN, vigencia y CVV dinamico. */
class TarjetaTest {

    /** Un PAN valido de prueba: 4999990000000007 supera Luhn. */
    private static final String VALIDO = "4999990000000007";

    @Test
    @DisplayName("Luhn: acepta el PAN correcto y rechaza cualquier error de un digito")
    void luhn() {
        assertThat(Pan.superaLuhn(VALIDO)).isTrue();
        assertThat(Pan.superaLuhn("4999990000000008")).isFalse();
        assertThat(Pan.superaLuhn("4539578763621486")).isTrue();
        assertThat(Pan.digitoDeControl("499999000000000")).isEqualTo('7');
    }

    @Test
    @DisplayName("Un PAN sin 16 digitos o que no supera Luhn no se puede construir")
    void panInvalido() {
        assertThatThrownBy(() -> new Pan("1234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pan(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pan("4999990000000008")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Por defecto solo se ven los ultimos 4: tambien si el PAN acaba en un log")
    void enmascarado() {
        Pan pan = new Pan(VALIDO);

        assertThat(pan.ultimos4()).isEqualTo("0007");
        assertThat(pan.enmascarado()).isEqualTo("•••• •••• •••• 0007");
        assertThat(pan).hasToString("•••• •••• •••• 0007");
        assertThat(pan.formateado()).isEqualTo("4999 9900 0000 0007");
    }

    @Test
    @DisplayName("El generador produce PAN de 16 digitos sobre el BIN de pruebas que superan Luhn")
    void generador() {
        GeneradorDePan generador = new GeneradorDePan(new Random(42));
        for (int i = 0; i < 200; i++) {
            Pan pan = generador.generar();
            assertThat(pan.valor()).hasSize(16).startsWith(GeneradorDePan.BIN_DE_PRUEBAS);
            assertThat(Pan.superaLuhn(pan.valor())).isTrue();
        }
    }

    @Test
    @DisplayName("La tarjeta nace ACTIVA y vence a los 4 anos, el mes de emision en hora de Lima")
    void emision() {
        // 1 de noviembre a las 02:00 UTC todavia es 31 de octubre en Lima.
        Tarjeta tarjeta = Tarjeta.emitir(UUID.randomUUID(), UUID.randomUUID(), new Pan(VALIDO),
                Instant.parse("2026-11-01T02:00:00Z"));

        assertThat(tarjeta.estado()).isEqualTo(EstadoDeTarjeta.ACTIVA);
        assertThat(tarjeta.venceEn()).isEqualTo(YearMonth.of(2030, 10));
        assertThat(tarjeta.vencimiento()).isEqualTo("10/30");
        assertThat(tarjeta.toString()).doesNotContain(VALIDO);
    }

    @Test
    @DisplayName("CVV dinamico: tres digitos, estable dentro de su ventana y distinto en otras")
    void cvvDinamico() {
        CalculadoraDeCvv cvv = new CalculadoraDeCvv(new byte[32]);
        Pan pan = new Pan(VALIDO);
        Instant inicio = Instant.parse("2026-10-28T15:00:00Z");

        String primero = cvv.calcular(pan, inicio);
        assertThat(primero).matches("\\d{3}");
        assertThat(cvv.calcular(pan, inicio.plusSeconds(299))).isEqualTo(primero);
        assertThat(cvv.validoHasta(inicio)).isEqualTo(inicio.plus(CalculadoraDeCvv.VENTANA));

        long distintos = java.util.stream.IntStream.range(1, 20)
                .mapToObj(i -> cvv.calcular(pan, inicio.plus(Duration.ofMinutes(5L * i))))
                .filter(otro -> !otro.equals(primero)).count();
        assertThat(distintos).isGreaterThan(15);
    }

    @Test
    @DisplayName("El CVV depende de la clave: sin ella no se puede calcular, y una clave corta no se acepta")
    void cvvDependeDeLaClave() {
        Pan pan = new Pan(VALIDO);
        Instant ahora = Instant.parse("2026-10-28T15:00:00Z");
        byte[] otra = new byte[32];
        otra[0] = 1;

        assertThat(new CalculadoraDeCvv(otra).calcular(pan, ahora))
                .isNotEqualTo(new CalculadoraDeCvv(new byte[32]).calcular(pan, ahora));
        assertThatThrownBy(() -> new CalculadoraDeCvv(new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }
}
