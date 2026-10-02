package pe.ayni.bank.core.domain.model;

/**
 * Los dos sentidos de un asiento.
 *
 * <p>El signo lo lleva el tipo y nunca el importe. Un CARGO de -50 seria un abono
 * disfrazado, y bastaria uno para que ninguna suma volviera a cuadrar. Por eso el importe
 * es siempre positivo y la restriccion de la base lo exige.
 */
public enum TipoDeAsiento {

    /** Sale dinero de la cuenta. */
    CARGO,
    /** Entra dinero en la cuenta. */
    ABONO
}
