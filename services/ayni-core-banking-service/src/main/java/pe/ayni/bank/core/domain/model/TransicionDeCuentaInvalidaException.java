package pe.ayni.bank.core.domain.model;

/** Se intento llevar una cuenta a un estado al que no puede pasar desde el actual. */
public class TransicionDeCuentaInvalidaException extends RuntimeException {

    public TransicionDeCuentaInvalidaException(EstadoCuenta desde, EstadoCuenta hasta) {
        super("Una cuenta " + desde + " no puede pasar a " + hasta + ".");
    }
}
