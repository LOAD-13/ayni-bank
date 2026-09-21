package pe.ayni.bank.identity.domain.model;

/**
 * Se lanza cuando el código ingresado para un desafío no coincide con el hash almacenado.
 */
public class CodigoDesafioInvalidoException extends RuntimeException {

    public CodigoDesafioInvalidoException() {
        super("El código de verificación ingresado es incorrecto.");
    }
}
