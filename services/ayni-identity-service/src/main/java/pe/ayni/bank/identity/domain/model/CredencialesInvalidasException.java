package pe.ayni.bank.identity.domain.model;

/**
 * Correo o contrasena incorrectos.
 *
 * <p><strong>El mensaje es deliberadamente ambiguo y no debe precisarse nunca.</strong>
 * Distinguir «ese correo no existe» de «esa contrasena no es» convierte el formulario de
 * ingreso en un comprobador de cuentas: quien tenga una lista de correos averigua cuales
 * son clientes de Ayni sin necesidad de adivinar ni una contrasena. Es el mismo criterio
 * que sostiene ADR-0008 en el registro.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public static final String MENSAJE_NEUTRO =
            "El correo o la contrasena no son correctos.";

    public CredencialesInvalidasException() {
        super(MENSAJE_NEUTRO);
    }
}
