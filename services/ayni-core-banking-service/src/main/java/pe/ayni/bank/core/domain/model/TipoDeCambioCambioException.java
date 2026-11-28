package pe.ayni.bank.core.domain.model;

/**
 * El tipo que el cliente vio ya no es el que se aplicaria (HU-12): cambio la referencia oficial, el
 * segmento, o ahora viene de la ultima cotizacion conocida y el cliente no lo sabia. No se convierte:
 * se le devuelve la cotizacion nueva para que decida con ella delante.
 */
public class TipoDeCambioCambioException extends RuntimeException {

    private final transient Cotizacion nueva;
    private final Moneda origen;

    public TipoDeCambioCambioException(Cotizacion nueva, Moneda origen) {
        super("El tipo de cambio cambio mientras confirmabas. Revisa el nuevo tipo antes de continuar.");
        this.nueva = nueva;
        this.origen = origen;
    }

    /** La moneda que entregaba el cliente: la cotizacion depende del sentido. */
    public Moneda origen() {
        return origen;
    }

    public Cotizacion nueva() {
        return nueva;
    }
}
