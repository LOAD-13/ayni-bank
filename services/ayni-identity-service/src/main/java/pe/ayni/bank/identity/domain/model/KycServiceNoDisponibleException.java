package pe.ayni.bank.identity.domain.model;

/**
 * kyc-service no respondio tras agotar reintentos, o el circuito esta abierto.
 *
 * <p>Que hacer ante esto — derivar la solicitud a revision manual, actualizar
 * {@code solicitud_onboarding.estado} — es el alcance de AYNI-13 subtarea 11
 * ("limite de tres intentos y derivacion a revision manual"), no de esta
 * excepcion en si. Ver diseno-base.md §3.4-3.5 y ADR-0020.
 */
public class KycServiceNoDisponibleException extends RuntimeException {

    public KycServiceNoDisponibleException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
