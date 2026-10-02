package pe.ayni.bank.core.domain.model;

/** Que clase de operacion origino un movimiento. */
public enum TipoDeMovimiento {

    /** Entre dos cuentas Ayni · HU-07. */
    TRANSFERENCIA,
    /** Entrada de dinero simulada, contra la cuenta tecnica de fondeo. */
    DEPOSITO_SIMULADO
}
