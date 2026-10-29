package pe.ayni.bank.core.domain.model;

/** El titular aun no tiene tarjeta: la web muestra «tu tarjeta aun no esta lista». */
public class SinTarjetaException extends RuntimeException {

    public SinTarjetaException() {
        super("Todavia no tienes una tarjeta.");
    }
}
