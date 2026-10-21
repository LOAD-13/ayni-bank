package pe.ayni.bank.core.domain.model;

/** Lo que queda en la pista de auditoria de cada operacion monetaria. */
public enum TipoDeEventoDeOperacion {
    TRANSFERENCIA_REALIZADA,
    DEPOSITO_SIMULADO,
    /** La misma clave de idempotencia llego otra vez: se devolvio el comprobante original. */
    OPERACION_REPETIDA,
    OPERACION_RECHAZADA,
    /** HU-06: el interes del mes se abono en la cuenta. */
    INTERES_CAPITALIZADO,
    /**
     * HU-05: se intento abrir una cuenta que el titular ya tiene. No es una operacion
     * monetaria, asi que no es un {@link #OPERACION_RECHAZADA} ni cuenta en sus metricas.
     */
    APERTURA_RECHAZADA
}
