package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Un importe con su moneda.
 *
 * <p><strong>{@link BigDecimal} y jamas {@code double} ni {@code float}.</strong> No es una
 * preferencia de estilo. Un {@code double} no puede representar 0.10 exactamente —en
 * binario es periodico, igual que 1/3 en decimal—, asi que sumar diez veces diez centimos
 * no da un sol. En una calculadora eso es una curiosidad; en un sistema que acumula
 * millones de operaciones es dinero que aparece o desaparece sin que nadie sepa de donde.
 *
 * <p><strong>El redondeo es HALF_EVEN.</strong> El redondeo habitual —HALF_UP, el del
 * colegio— empuja siempre hacia arriba en los empates, y sobre muchas operaciones eso
 * introduce un sesgo acumulado a favor de una de las partes. HALF_EVEN reparte los empates
 * alternando, de modo que el sesgo tiende a cero. Es lo que usa la banca y por lo que se
 * llama tambien «redondeo del banquero».
 *
 * <p>Dos monedas distintas no se suman. No hay conversion implicita: convertir exige un
 * tipo de cambio, y un tipo de cambio exige saber de que dia es y con que margen.
 */
public record Dinero(BigDecimal importe, Moneda moneda) {

    /** Los dos decimales del sol y del dolar. */
    public static final int DECIMALES = 2;

    /** El redondeo de toda operacion monetaria del sistema. */
    public static final RoundingMode REDONDEO = RoundingMode.HALF_EVEN;

    public Dinero {
        Objects.requireNonNull(importe, "El importe es obligatorio.");
        Objects.requireNonNull(moneda, "La moneda es obligatoria.");
        // Se normaliza a dos decimales al construir. Sin esto, 10.00 y 10.000 serian
        // objetos distintos que no se consideran iguales, y las comparaciones de un record
        // fallarian por una diferencia que no existe.
        importe = importe.setScale(DECIMALES, REDONDEO);
    }

    public static Dinero de(String importe, Moneda moneda) {
        // Desde String y no desde double: `new BigDecimal(0.1)` guarda el error del double
        // dentro del BigDecimal, que es la forma mas silenciosa de arruinar la exactitud.
        return new Dinero(new BigDecimal(importe), moneda);
    }

    public static Dinero cero(Moneda moneda) {
        return new Dinero(BigDecimal.ZERO, moneda);
    }

    public Dinero mas(Dinero otro) {
        exigirLaMismaMoneda(otro);
        return new Dinero(importe.add(otro.importe), moneda);
    }

    public Dinero menos(Dinero otro) {
        exigirLaMismaMoneda(otro);
        return new Dinero(importe.subtract(otro.importe), moneda);
    }

    public boolean esCero() {
        return importe.signum() == 0;
    }

    public boolean esPositivo() {
        return importe.signum() > 0;
    }

    public boolean esNegativo() {
        return importe.signum() < 0;
    }

    private void exigirLaMismaMoneda(Dinero otro) {
        if (moneda != otro.moneda) {
            throw new MonedasIncompatiblesException(moneda, otro.moneda);
        }
    }

    /** Apto para pantalla: {@code S/ 12,480.65}. */
    public String formateado() {
        return moneda.simbolo() + " " + importe.toPlainString();
    }

    @Override
    public String toString() {
        return importe.toPlainString() + " " + moneda;
    }
}
