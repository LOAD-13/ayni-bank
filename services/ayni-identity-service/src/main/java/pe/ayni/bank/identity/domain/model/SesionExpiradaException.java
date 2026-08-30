package pe.ayni.bank.identity.domain.model;

/** El token de renovacion caduco. No es un incidente de seguridad: son siete dias. */
public class SesionExpiradaException extends RuntimeException {

    public SesionExpiradaException() {
        super("Tu sesion caduco. Vuelve a iniciar sesion.");
    }
}
