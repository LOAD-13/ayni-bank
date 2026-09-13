package pe.ayni.bank.identity.domain.model;

/**
 * Estado inicial de una verificacion recien iniciada en kyc-service.
 *
 * <p>Deliberadamente minimo: {@code VerificationResponse} (la respuesta de
 * {@code POST /kyc/verify}) solo puede traer "pending" — los estados finales
 * (aprobado/rechazado) llegan consultando despues {@code VerificationStatus}/
 * {@code VerificationResult}, fuera del alcance de esta subtarea (AYNI-13
 * subtarea 10 solo envuelve el inicio de la verificacion).
 */
public enum EstadoDeVerificacionKyc {
    PENDIENTE
}
