package pe.ayni.bank.identity.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Numero de telefono movil peruano: nueve digitos que empiezan en 9.
 *
 * <p>El 9 inicial no es una convencion nuestra. En el plan de numeracion del Peru, todos
 * los moviles empiezan en 9 desde 2008; un numero de nueve digitos que empieza por otra
 * cifra no es un movil y no puede recibir el codigo de verificacion.
 *
 * <p>Se aceptan espacios y guiones al construirlo porque la gente los escribe, y se
 * eliminan al normalizar. Rechazar «987 654 321» seria pedantear con el usuario sobre
 * algo que el sistema puede resolver solo.
 */
public record Celular(String valor) {

    private static final Pattern FORMATO = Pattern.compile("^9[0-9]{8}$");
    private static final Pattern SEPARADORES = Pattern.compile("[\\s-]");

    public Celular {
        Objects.requireNonNull(valor, "El numero de celular es obligatorio.");
        valor = SEPARADORES.matcher(valor.trim()).replaceAll("");
        if (!FORMATO.matcher(valor).matches()) {
            throw new IllegalArgumentException(
                    "El celular debe tener nueve digitos y empezar en 9.");
        }
    }

    /**
     * Representacion enmascarada, apta para diagnostico. Deja visibles los tres ultimos
     * digitos, que es lo que permite a la persona reconocer su numero sin exponerlo.
     */
    public String enmascarado() {
        return "******" + valor.substring(6);
    }
}
