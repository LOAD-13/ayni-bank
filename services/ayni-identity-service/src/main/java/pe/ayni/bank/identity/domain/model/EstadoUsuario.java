package pe.ayni.bank.identity.domain.model;

/**
 * Estados por los que pasa un usuario. El registro deja siempre
 * {@link #PENDIENTE_VERIFICACION}: existir en la base de datos y poder operar con dinero
 * son dos cosas distintas, y confundirlas es como se abren cuentas sin identificar a su
 * titular.
 */
public enum EstadoUsuario {

    /** Registrado, sin KYC superado. No puede operar. */
    PENDIENTE_VERIFICACION,

    /** KYC superado. Opera con normalidad. */
    ACTIVO,

    /** KYC derivado a revision manual del Oficial de Cumplimiento. */
    EN_REVISION,

    /** Bloqueado por intentos fallidos o por decision del banco. */
    BLOQUEADO;

    /**
     * Solo un usuario ACTIVO puede operar. Se expresa como pregunta al dominio y no como
     * comparaciones sueltas repartidas por el codigo: cuando manana aparezca un estado
     * nuevo, hay un unico sitio que revisar.
     */
    public boolean puedeOperar() {
        return this == ACTIVO;
    }
}
