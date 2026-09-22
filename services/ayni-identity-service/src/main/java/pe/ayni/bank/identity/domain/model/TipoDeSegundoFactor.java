package pe.ayni.bank.identity.domain.model;

/**
 * Métodos de segundo factor de autenticación (2FA) soportados en Ayni Bank.
 * Conforme a la especificación de HU-22 (AYNI-124).
 */
public enum TipoDeSegundoFactor {

    /** App de autenticación (TOTP). Método recomendado y por defecto. */
    APP_AUTENTICADORA,

    /** Código de un solo uso enviado por correo electrónico. */
    CORREO_ELECTRONICO,

    /** Mensaje de texto SMS (Próximamente / Deshabilitado). */
    SMS
}
