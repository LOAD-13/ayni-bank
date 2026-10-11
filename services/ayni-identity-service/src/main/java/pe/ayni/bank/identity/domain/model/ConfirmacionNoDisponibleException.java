package pe.ayni.bank.identity.domain.model;

/** La confirmacion no existe, es de otro usuario, caduco, se uso o agoto sus intentos. */
public class ConfirmacionNoDisponibleException extends RuntimeException {

    public ConfirmacionNoDisponibleException() {
        super("La confirmacion ya no es valida. Vuelve a confirmar la operacion.");
    }
}
