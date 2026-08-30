package pe.ayni.bank.identity.domain.model;

/**
 * Secreto compartido del segundo factor, en Base32.
 *
 * <p>Base32 y no Base64 porque es lo que entienden Google Authenticator, Authy y el resto:
 * el URI {@code otpauth://} lo exige, y un secreto en Base64 sencillamente no se puede dar
 * de alta en ninguna de esas aplicaciones.
 *
 * <p>Es tan sensible como una contraseña —quien lo tenga genera códigos válidos para
 * siempre—, así que se guarda cifrado con AES-256-GCM y {@link #toString()} no lo devuelve.
 * A diferencia de la contraseña, no se puede derivar: el servidor necesita el valor original
 * para calcular el código y compararlo.
 */
public record SecretoTotp(String valor) {

    /** 160 bits en Base32 son 32 caracteres, que es lo que recomienda la RFC 4226. */
    private static final int LONGITUD = 32;

    private static final String ALFABETO_BASE32 = "^[A-Z2-7]+$";

    public SecretoTotp {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El secreto del segundo factor es obligatorio.");
        }
        valor = valor.trim().toUpperCase();
        if (valor.length() != LONGITUD) {
            throw new IllegalArgumentException(
                    "El secreto del segundo factor debe tener " + LONGITUD + " caracteres.");
        }
        if (!valor.matches(ALFABETO_BASE32)) {
            throw new IllegalArgumentException("El secreto del segundo factor no es Base32.");
        }
    }

    public static int longitud() {
        return LONGITUD;
    }

    @Override
    public String toString() {
        return "SecretoTotp[oculto]";
    }
}
