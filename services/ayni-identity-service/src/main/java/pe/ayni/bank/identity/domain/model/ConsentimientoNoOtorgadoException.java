package pe.ayni.bank.identity.domain.model;

/**
 * Se intento registrar a alguien que no acepto los terminos y la politica de datos
 * personales. Escenario 4 de HU-01.
 */
public class ConsentimientoNoOtorgadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConsentimientoNoOtorgadoException() {
        super("Debes aceptar los terminos y la politica de datos personales para continuar.");
    }
}
