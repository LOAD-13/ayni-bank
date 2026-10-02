package pe.ayni.bank.core.domain.model;

/**
 * El titular ya tiene una cuenta activa en esa moneda. Escenario 2 de HU-05.
 *
 * <p>La regla la impone ademas un indice unico parcial en la base: dos peticiones
 * simultaneas pueden superar a la vez cualquier comprobacion previa en memoria, y esa
 * carrera solo la resuelve la base.
 */
public class CuentaDuplicadaException extends RuntimeException {

    public CuentaDuplicadaException(Moneda moneda) {
        super("El titular ya tiene una cuenta de ahorro activa en " + moneda + ".");
    }
}
