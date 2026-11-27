package pe.ayni.bank.core.domain.model;

/** Quien inicio una operacion sensible intento aprobarla tambien (HU-15, AYNI-282). */
public class CuatroOjosException extends RuntimeException {

    public CuatroOjosException() {
        super("Quien inicia una operacion sensible no puede aprobarla: la aprueba otra persona.");
    }
}
