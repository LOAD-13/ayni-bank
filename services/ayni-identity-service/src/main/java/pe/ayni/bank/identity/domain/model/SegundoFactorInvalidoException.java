package pe.ayni.bank.identity.domain.model;

/** El codigo de verificacion no corresponde al secreto, o llego fuera de su ventana. */
public class SegundoFactorInvalidoException extends RuntimeException {

    public SegundoFactorInvalidoException() {
        super("El codigo de verificacion no es valido o ya vencio.");
    }
}
