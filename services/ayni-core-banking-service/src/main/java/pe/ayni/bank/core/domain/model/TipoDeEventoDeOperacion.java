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
    APERTURA_RECHAZADA,
    /**
     * HU-08: se pidio el comprobante de un movimiento de otra persona y se denego. Evento de
     * seguridad: guarda el movimiento que se intento leer, nunca su contenido.
     */
    ACCESO_AJENO_DENEGADO,
    /** HU-11: el cargo de una transferencia a otro banco, a la espera de la camara. */
    INTERBANCARIA_ENVIADA,
    INTERBANCARIA_COMPLETADA,
    /** HU-11: la devolucion del importe porque la camara la rechazo o no respondio a tiempo. */
    INTERBANCARIA_REVERTIDA,
    /** HU-09: la tarjeta de debito virtual emitida; el recurso es la tarjeta. */
    TARJETA_EMITIDA,
    /** HU-09: el titular vio el PAN completo tras confirmar con segundo factor; con su IP. */
    PAN_REVELADO,
    /** HU-10: el titular congelo su tarjeta de debito virtual; el recurso es la tarjeta. */
    TARJETA_CONGELADA,
    /** HU-10: el titular descongelo su tarjeta de debito virtual; el recurso es la tarjeta. */
    TARJETA_DESCONGELADA,
    /** HU-10: el titular actualizo el limite diario o las compras por internet. */
    CONTROL_TARJETA_ACTUALIZADO,
    /** HU-10: un consumo con tarjeta fue aprobado y cargado en la cuenta. */
    CONSUMO_TARJETA_REALIZADO
}
