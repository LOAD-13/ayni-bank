package pe.ayni.bank.core.domain.model;

/**
 * La fuente oficial no respondio o no trajo un tipo de cambio utilizable. Quien la llama decide
 * si usa el ultimo valor conocido (HU-12, escenario 2).
 */
public class FuenteDeTipoDeCambioNoDisponibleException extends RuntimeException {

    public FuenteDeTipoDeCambioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public FuenteDeTipoDeCambioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
