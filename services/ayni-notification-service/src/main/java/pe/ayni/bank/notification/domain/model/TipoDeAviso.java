package pe.ayni.bank.notification.domain.model;

/** Los avisos de HU-13. El nombre coincide con el codigo de la plantilla de correo. */
public enum TipoDeAviso {
    TRANSFERENCIA_ENVIADA,
    TRANSFERENCIA_RECIBIDA,
    DEPOSITO_RECIBIDO,
    /** HU-06: el interes del mes se abono en la cuenta. */
    INTERES_CAPITALIZADO,
    /** HU-05 escenario 3: un operador rechazo la solicitud de apertura. Lleva el motivo. */
    SOLICITUD_RECHAZADA
}
