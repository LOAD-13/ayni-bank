package pe.ayni.bank.identity.domain.model;

/**
 * Requisitos que toda contrasena debe cumplir, cada uno con el mensaje que se le muestra
 * a la persona cuando lo incumple.
 *
 * <p>Son un enumerado y no cadenas sueltas porque el escenario 3 de HU-01 exige indicar
 * <em>que requisito concreto</em> falla. Con una excepcion generica —«la contrasena no es
 * valida»— el usuario prueba a ciegas hasta que acierta o abandona.
 *
 * <p>Decir cual es el requisito no ayuda a un atacante: la politica esta publicada en la
 * propia pantalla de registro antes de que nadie escriba nada. Lo que si seria un fallo es
 * revelar informacion sobre la contrasena <em>ya existente</em> de otra persona, y eso no
 * ocurre aqui.
 */
public enum RequisitoDeContrasena {

    LONGITUD_MINIMA("La contrasena debe tener al menos 12 caracteres."),
    MAYUSCULA("La contrasena debe incluir al menos una letra mayuscula."),
    MINUSCULA("La contrasena debe incluir al menos una letra minuscula."),
    DIGITO("La contrasena debe incluir al menos un digito."),
    SIMBOLO("La contrasena debe incluir al menos un simbolo.");

    private final String mensaje;

    RequisitoDeContrasena(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
