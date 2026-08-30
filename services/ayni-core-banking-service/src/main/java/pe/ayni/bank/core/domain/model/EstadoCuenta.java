package pe.ayni.bank.core.domain.model;

/** Estados por los que pasa una cuenta. */
public enum EstadoCuenta {

    ACTIVA,
    /** Congelada por seguridad o por orden judicial. Ni cargos ni abonos. */
    BLOQUEADA,
    CERRADA;

    public boolean admiteMovimientos() {
        return this == ACTIVA;
    }
}
