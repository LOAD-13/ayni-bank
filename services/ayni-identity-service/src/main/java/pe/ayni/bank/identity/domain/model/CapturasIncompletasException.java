package pe.ayni.bank.identity.domain.model;

/** Se pidio un paso que necesita algo anterior: leer sin las dos fotos, o confirmar sin lectura. */
public class CapturasIncompletasException extends RuntimeException {

    public CapturasIncompletasException(String motivo) {
        super(motivo);
    }
}
