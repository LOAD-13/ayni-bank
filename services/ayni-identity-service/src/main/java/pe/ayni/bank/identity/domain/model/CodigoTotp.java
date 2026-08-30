package pe.ayni.bank.identity.domain.model;

/**
 * Código de seis dígitos que el usuario copia de su aplicación de autenticación.
 *
 * <p>Se conserva como texto y no como número: un código puede empezar por cero, y un
 * {@code int} se lo comería. {@code 042713} y {@code 42713} no son el mismo código.
 */
public record CodigoTotp(String valor) {

    public static final int DIGITOS = 6;

    public CodigoTotp {
        if (valor == null) {
            throw new IllegalArgumentException("El codigo de verificacion es obligatorio.");
        }
        // Las aplicaciones de autenticación muestran «049 713» con un espacio en medio, y
        // eso es lo que la gente copia. Rechazarlo por el espacio sería absurdo.
        valor = valor.replaceAll("\\s", "");
        if (!valor.matches("^[0-9]{" + DIGITOS + "}$")) {
            throw new IllegalArgumentException(
                    "El codigo de verificacion debe tener " + DIGITOS + " digitos.");
        }
    }

    /** El código es un secreto de un solo uso: no aparece en ningún log. */
    @Override
    public String toString() {
        return "CodigoTotp[oculto]";
    }
}
