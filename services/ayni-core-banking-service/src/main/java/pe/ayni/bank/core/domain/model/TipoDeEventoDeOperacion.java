package pe.ayni.bank.core.domain.model;

/** Lo que queda en la pista de auditoria de cada operacion monetaria. */
public enum TipoDeEventoDeOperacion {
    TRANSFERENCIA_REALIZADA,
    DEPOSITO_SIMULADO,
    /** La misma clave de idempotencia llego otra vez: se devolvio el comprobante original. */
    OPERACION_REPETIDA,
    OPERACION_RECHAZADA
}
