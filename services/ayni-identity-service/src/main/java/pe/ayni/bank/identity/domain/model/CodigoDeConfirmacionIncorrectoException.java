package pe.ayni.bank.identity.domain.model;

/** El codigo del segundo factor no es correcto. Dice cuantos intentos le quedan a la confirmacion. */
public class CodigoDeConfirmacionIncorrectoException extends RuntimeException {

    private final int intentosRestantes;

    public CodigoDeConfirmacionIncorrectoException(int intentosRestantes) {
        super("El codigo no es correcto.");
        this.intentosRestantes = intentosRestantes;
    }

    public int intentosRestantes() {
        return intentosRestantes;
    }
}
