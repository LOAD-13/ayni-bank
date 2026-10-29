package pe.ayni.bank.core.domain.service;

import java.util.Objects;
import java.util.random.RandomGenerator;

import pe.ayni.bank.core.domain.model.Pan;

/**
 * Genera PAN de dieciseis digitos sobre el BIN de pruebas de Ayni · HU-09.
 *
 * <p>El BIN (los seis primeros digitos) lo asigna la marca de la tarjeta al emisor. Mientras
 * Ayni no tenga uno, se usa {@value #BIN_DE_PRUEBAS}, que empieza por 4 como los de prueba de
 * las pasarelas y no corresponde a ningun emisor real. Siguen nueve digitos al azar y el
 * digito de control de Luhn. La unicidad no es cosa del azar: la garantiza la emision, que
 * comprueba que el PAN no exista y genera otro si choca.
 */
public class GeneradorDePan {

    public static final String BIN_DE_PRUEBAS = "499999";
    private static final int DIGITOS_AL_AZAR = Pan.LONGITUD - BIN_DE_PRUEBAS.length() - 1;

    private final RandomGenerator azar;

    /** En produccion, un SecureRandom: un PAN predecible facilita adivinar los de otros. */
    public GeneradorDePan(RandomGenerator azar) {
        this.azar = Objects.requireNonNull(azar);
    }

    public Pan generar() {
        StringBuilder sinControl = new StringBuilder(BIN_DE_PRUEBAS);
        for (int i = 0; i < DIGITOS_AL_AZAR; i++) {
            sinControl.append(azar.nextInt(10));
        }
        return new Pan(sinControl.toString() + Pan.digitoDeControl(sinControl.toString()));
    }
}
