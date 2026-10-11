package pe.ayni.bank.notification.domain.model;

import java.time.Duration;

/**
 * Cuanto se espera entre intentos y cuantos se hacen.
 *
 * <p>La espera crece en potencias de dos: con una base de 30 s, el segundo intento sale a
 * los 30 s, el tercero al minuto, el cuarto a los 2 min. Un proveedor de correo caido se
 * recupera sin que se le golpee cada pocos segundos.
 *
 * @param base   la espera tras el primer fallo
 * @param maximo cuantos intentos se hacen en total antes de dar la notificacion por fallida
 */
public record PoliticaDeReintentos(Duration base, int maximo) {

    /** Tope del exponente: mas alla, la espera ya no tiene sentido y el calculo desborda. */
    private static final int EXPONENTE_MAXIMO = 20;

    public PoliticaDeReintentos {
        if (base == null || base.isNegative() || base.isZero()) {
            throw new IllegalArgumentException("La espera base tiene que ser positiva");
        }
        if (maximo < 1) {
            throw new IllegalArgumentException("Hace falta al menos un intento");
        }
    }

    /** La espera despues del fallo numero {@code intentos} (empieza en 1). */
    public Duration esperaTras(int intentos) {
        return base.multipliedBy(1L << Math.clamp(intentos - 1L, 0, EXPONENTE_MAXIMO));
    }

    public boolean seAgotaron(int intentos) {
        return intentos >= maximo;
    }
}
