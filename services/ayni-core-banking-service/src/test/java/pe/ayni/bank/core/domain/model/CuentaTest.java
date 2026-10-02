package pe.ayni.bank.core.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** La cuenta y su saldo derivado · HU-05. */
class CuentaTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final UUID TITULAR = UUID.randomUUID();
    /** Identificador del producto AHORRO_PEN sembrado por la migracion V1. */
    private static final short PRODUCTO = 1;

    private final Cuenta cuenta = Cuenta.abrir(UUID.randomUUID(), TITULAR, PRODUCTO,
            NumeroDeCuenta.de(Moneda.PEN, 9_876_543_210L), Moneda.PEN, AHORA);

    private Asiento asiento(TipoDeAsiento tipo, String importe) {
        return new Asiento(UUID.randomUUID(), cuenta.id(), UUID.randomUUID(), tipo,
                Dinero.de(importe, Moneda.PEN), "Movimiento de prueba", AHORA);
    }

    @Nested
    @DisplayName("Apertura")
    class Apertura {

        @Test
        @DisplayName("nace ACTIVA, en soles y con saldo cero")
        void naceComoDiceLaHistoria() {
            assertThat(cuenta.estado()).isEqualTo(EstadoCuenta.ACTIVA);
            assertThat(cuenta.moneda()).isEqualTo(Moneda.PEN);
            assertThat(cuenta.saldo(List.of())).isEqualTo(Dinero.cero(Moneda.PEN));
        }

        @Test
        @DisplayName("no hay forma de abrirla con saldo inicial")
        void noSePuedeCrearDineroDeLaNada() {
            // Meter dinero exige un asiento, y un asiento deja rastro. Que la única puerta
            // de entrada sea esa es lo que impide que aparezca dinero sin origen.
            assertThat(Cuenta.class.getDeclaredConstructors())
                    .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers()));
        }

        @Test
        void generaSuCciConLosDigitosDeControl() {
            assertThat(cuenta.cci().valor()).hasSize(20);
            assertThat(cuenta.cci().banco()).isEqualTo(Cci.CODIGO_DE_BANCO);
            assertThat(cuenta.cci().oficina()).isEqualTo(Cci.CODIGO_DE_OFICINA);
        }

        @Test
        @DisplayName("el número de cuenta y la moneda no pueden contradecirse")
        void rechazaUnNumeroDeOtraMoneda() {
            var numeroEnDolares = NumeroDeCuenta.de(Moneda.USD, 1L);

            assertThatThrownBy(() -> Cuenta.abrir(UUID.randomUUID(), TITULAR, PRODUCTO,
                    numeroEnDolares, Moneda.PEN, AHORA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Saldo")
    class Saldo {

        @Test
        @DisplayName("es la suma de los asientos, no un campo guardado")
        void seDerivaDeLosAsientos() {
            var asientos = List.of(
                    asiento(TipoDeAsiento.ABONO, "1000.00"),
                    asiento(TipoDeAsiento.CARGO, "250.50"),
                    asiento(TipoDeAsiento.ABONO, "1.58"));

            assertThat(cuenta.saldo(asientos)).isEqualTo(Dinero.de("751.08", Moneda.PEN));
        }

        @Test
        @DisplayName("los abonos suman y los cargos restan, sin importar el orden")
        void elOrdenNoAltera() {
            var uno = asiento(TipoDeAsiento.ABONO, "500.00");
            var otro = asiento(TipoDeAsiento.CARGO, "120.30");

            assertThat(cuenta.saldo(List.of(uno, otro)))
                    .isEqualTo(cuenta.saldo(List.of(otro, uno)));
        }

        @Test
        @DisplayName("no acepta asientos de otra cuenta")
        void rechazaAsientosAjenos() {
            var ajeno = new Asiento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    TipoDeAsiento.ABONO, Dinero.de("1.00", Moneda.PEN), "Ajeno", AHORA);

            assertThatThrownBy(() -> cuenta.saldo(List.of(ajeno)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Asientos")
    class Asientos {

        @Test
        @DisplayName("el importe siempre es positivo: el signo lo lleva el tipo")
        void rechazaImportesNoPositivos() {
            assertThatThrownBy(() -> asiento(TipoDeAsiento.CARGO, "-50.00"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> asiento(TipoDeAsiento.ABONO, "0.00"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void elEfectoDeUnCargoEsNegativo() {
            assertThat(asiento(TipoDeAsiento.CARGO, "50.00").efecto())
                    .isEqualTo(Dinero.de("-50.00", Moneda.PEN));
            assertThat(asiento(TipoDeAsiento.ABONO, "50.00").efecto())
                    .isEqualTo(Dinero.de("50.00", Moneda.PEN));
        }

        @Test
        @DisplayName("una operación por partida doble suma cero")
        void laPartidaDobleCuadra() {
            // Es el invariante que hace que el dinero no se cree ni se destruya: lo que sale
            // de un sitio entra en otro, y la suma del movimiento entero es cero.
            UUID movimiento = UUID.randomUUID();
            var cargo = new Asiento(UUID.randomUUID(), cuenta.id(), movimiento,
                    TipoDeAsiento.CARGO, Dinero.de("300.00", Moneda.PEN), "Transferencia",
                    AHORA);
            var abono = new Asiento(UUID.randomUUID(), UUID.randomUUID(), movimiento,
                    TipoDeAsiento.ABONO, Dinero.de("300.00", Moneda.PEN), "Transferencia",
                    AHORA);

            assertThat(cargo.efecto().mas(abono.efecto()).esCero()).isTrue();
        }

        @Test
        void exigeConcepto() {
            assertThatThrownBy(() -> new Asiento(UUID.randomUUID(), cuenta.id(),
                    UUID.randomUUID(), TipoDeAsiento.ABONO, Dinero.de("1.00", Moneda.PEN),
                    "   ", AHORA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Estados")
    class Estados {

        @Test
        void unaCuentaBloqueadaNoAdmiteMovimientos() {
            assertThat(cuenta.admiteMovimientos()).isTrue();
            assertThat(cuenta.bloquear().admiteMovimientos()).isFalse();
        }

        @Test
        void unaCuentaCerradaNoSePuedeBloquear() {
            Cuenta cerrada = Cuenta.reconstituir(cuenta.id(), TITULAR, PRODUCTO,
                    cuenta.numero(), cuenta.cci(), Moneda.PEN, EstadoCuenta.CERRADA, AHORA);

            assertThatThrownBy(cerrada::bloquear)
                    .isInstanceOf(TransicionDeCuentaInvalidaException.class);
        }

        @Test
        @DisplayName("toString no expone el numero ni el CCI: identifican al titular")
        void noFiltraLosIdentificadores() {
            assertThat(cuenta.toString())
                    .doesNotContain(cuenta.numero().valor())
                    .doesNotContain(cuenta.cci().valor());
        }
    }
}
