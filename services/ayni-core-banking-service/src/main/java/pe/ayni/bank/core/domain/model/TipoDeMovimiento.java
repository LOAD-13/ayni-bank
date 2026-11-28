package pe.ayni.bank.core.domain.model;

/** Que clase de operacion origino un movimiento. */
public enum TipoDeMovimiento {

    /** Entre dos cuentas Ayni · HU-07. */
    TRANSFERENCIA,
    /** Entrada de dinero simulada, contra la cuenta tecnica de fondeo. */
    DEPOSITO_SIMULADO,
    /** Capitalizacion mensual de intereses, contra la cuenta tecnica de intereses · HU-06. */
    INTERES,
    /** Una de las dos patas de un cambio de moneda (HU-12): cliente y posicion cambiaria. */
    CONVERSION
}
