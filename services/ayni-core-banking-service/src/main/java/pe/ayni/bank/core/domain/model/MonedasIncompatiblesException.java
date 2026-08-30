package pe.ayni.bank.core.domain.model;

/**
 * Se intento operar con importes de monedas distintas.
 *
 * <p>Es una excepcion y no una conversion automatica porque convertir exige un tipo de
 * cambio, y un tipo de cambio exige saber de que dia es y con que margen se aplica. Un
 * sistema que convierte por su cuenta acaba usando el tipo equivocado sin que nadie lo
 * note.
 */
public class MonedasIncompatiblesException extends RuntimeException {

    public MonedasIncompatiblesException(Moneda una, Moneda otra) {
        super("No se pueden operar importes en " + una + " y " + otra
                + " sin convertir primero.");
    }
}
