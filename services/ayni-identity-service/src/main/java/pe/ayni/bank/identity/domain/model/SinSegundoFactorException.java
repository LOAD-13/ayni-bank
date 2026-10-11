package pe.ayni.bank.identity.domain.model;

/** El titular no tiene un segundo factor activo con el que confirmar la operacion. */
public class SinSegundoFactorException extends RuntimeException {

    public SinSegundoFactorException() {
        super("Activa tu segundo factor para poder confirmar operaciones.");
    }
}
