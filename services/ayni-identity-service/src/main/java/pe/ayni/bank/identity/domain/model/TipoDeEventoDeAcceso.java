package pe.ayni.bank.identity.domain.model;

/** Lo que se anota en la pista de auditoria. HU-04 exige registrar exitos y fracasos. */
public enum TipoDeEventoDeAcceso {

    INGRESO_EXITOSO,
    CREDENCIALES_INVALIDAS,
    SEGUNDO_FACTOR_INVALIDO,
    INGRESO_BLOQUEADO,
    SEGUNDO_FACTOR_INSCRITO,
    SESION_RENOVADA,
    /** Escenario 4: se presento un token ya consumido y se invalido la familia. */
    REUTILIZACION_DE_TOKEN
}
