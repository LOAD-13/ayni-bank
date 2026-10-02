package pe.ayni.bank.identity.domain.model;

import java.util.regex.Pattern;

/**
 * Documentos de identidad que Ayni admite para abrir una cuenta.
 *
 * <p>Cada tipo lleva su propio formato porque no comparten ninguno: el DNI peruano son
 * ocho digitos exactos, el carne de extranjeria mezcla letras y numeros, y el pasaporte
 * varia segun el pais emisor. Una unica expresion regular para los tres tendria que ser
 * tan laxa que no validaria nada.
 *
 * <p>La lista coincide con la restriccion {@code ck_persona_tipo_documento} de la
 * migracion V2. Anadir un tipo aqui exige una migracion que amplie esa restriccion.
 */
public enum TipoDocumento {

    DNI(Pattern.compile("^[0-9]{8}$"), "El DNI debe tener ocho digitos."),

    CE(Pattern.compile("^[0-9A-Z]{9,12}$"),
            "El carne de extranjeria debe tener entre nueve y doce caracteres."),

    PASAPORTE(Pattern.compile("^[0-9A-Z]{6,12}$"),
            "El pasaporte debe tener entre seis y doce caracteres.");

    private final Pattern formato;
    private final String mensajeDeError;

    TipoDocumento(Pattern formato, String mensajeDeError) {
        this.formato = formato;
        this.mensajeDeError = mensajeDeError;
    }

    /**
     * Traduce el valor que llega del formulario.
     *
     * <p>No se usa {@code valueOf} directamente porque lanza un mensaje que menciona la
     * clase Java, y eso acaba en la respuesta HTTP a traves del manejador de errores.
     */
    public static TipoDocumento de(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio.");
        }
        String normalizado = valor.trim().toUpperCase();
        for (TipoDocumento tipo : values()) {
            if (tipo.name().equals(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException(
                "El tipo de documento debe ser DNI, CE o PASAPORTE.");
    }

    boolean admite(String numero) {
        return formato.matcher(numero).matches();
    }

    String mensajeDeError() {
        return mensajeDeError;
    }

    /** El DNI no lleva letras; los otros dos si, y siempre en mayusculas. */
    boolean distingueMayusculas() {
        return this != DNI;
    }
}
