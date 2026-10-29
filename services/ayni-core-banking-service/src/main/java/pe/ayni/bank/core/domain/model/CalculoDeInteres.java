package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * El interes de un dia: {@code saldo_cierre × ((1 + TEA)^(1/360) − 1)}, con año comercial
 * de 360 dias como en el sistema financiero peruano (diseño §2.3).
 *
 * <p>{@link BigDecimal} no sabe elevar a una potencia fraccionaria, y pasar por
 * {@code Math.pow} seria usar {@code double}, que esta prohibido. La raiz 360 se calcula
 * con el metodo de Newton en aritmetica decimal de 34 digitos: converge en pocas vueltas
 * y el error queda muchos ordenes por debajo del octavo decimal que se guarda.
 */
public final class CalculoDeInteres {

    /** Dias del año comercial. */
    public static final int DIAS_DEL_ANO = 360;
    /** Decimales del devengo diario: NUMERIC(19,8) en la base (diseño §2.3). */
    public static final int DECIMALES_DEL_DEVENGO = 8;

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal TOLERANCIA = new BigDecimal("1E-32");
    private static final int VUELTAS_MAXIMAS = 100;

    private CalculoDeInteres() {
    }

    /** {@code (1 + TEA)^(1/360) − 1}, con 34 digitos significativos. */
    public static BigDecimal factorDiario(BigDecimal tea) {
        if (tea.signum() < 0) {
            throw new IllegalArgumentException("La TEA no puede ser negativa");
        }
        return raiz(BigDecimal.ONE.add(tea), DIAS_DEL_ANO).subtract(BigDecimal.ONE, PRECISION);
    }

    /** El interes de un dia sobre el saldo de cierre, redondeado a 8 decimales con HALF_EVEN. */
    public static BigDecimal interesDelDia(BigDecimal saldoDeCierre, BigDecimal tea) {
        return saldoDeCierre.multiply(factorDiario(tea), PRECISION)
                .setScale(DECIMALES_DEL_DEVENGO, RoundingMode.HALF_EVEN);
    }

    /** Raiz n-esima por Newton: x ← ((n−1)·x + a / x^(n−1)) / n. */
    static BigDecimal raiz(BigDecimal a, int n) {
        BigDecimal enesimo = BigDecimal.valueOf(n);
        BigDecimal anterior = BigDecimal.valueOf(n - 1L);
        // 1 + TEA/n ya esta muy cerca de la raiz: Newton arranca a pocas cifras del resultado.
        BigDecimal x = BigDecimal.ONE.add(a.subtract(BigDecimal.ONE).divide(enesimo, PRECISION), PRECISION);
        for (int i = 0; i < VUELTAS_MAXIMAS; i++) {
            BigDecimal siguiente = anterior.multiply(x, PRECISION)
                    .add(a.divide(x.pow(n - 1, PRECISION), PRECISION), PRECISION)
                    .divide(enesimo, PRECISION);
            if (siguiente.subtract(x).abs().compareTo(TOLERANCIA) <= 0) {
                return siguiente;
            }
            x = siguiente;
        }
        return x;
    }
}
