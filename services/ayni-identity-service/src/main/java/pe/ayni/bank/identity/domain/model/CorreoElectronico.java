package pe.ayni.bank.identity.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Correo electronico de un usuario, ya validado y normalizado.
 *
 * <p>La normalizacion a minusculas no es cosmetica: sin ella, {@code Ana@ayni.pe} y
 * {@code ana@ayni.pe} conviven como dos cuentas distintas y la unicidad del correo deja
 * de significar nada. El dominio de un correo es insensible a mayusculas por RFC, y la
 * parte local lo es en la practica en todos los proveedores que la gente usa.
 *
 * <p>El patron es deliberadamente conservador. Validar correos con la gramatica completa
 * del RFC 5322 produce expresiones ilegibles que aceptan direcciones que ningun proveedor
 * real emite. Lo que de verdad confirma que un correo existe es enviarle un mensaje.
 */
public record CorreoElectronico(String valor) {

    /** Limite del RFC 5321 para la direccion completa. */
    private static final int LONGITUD_MAXIMA = 254;

    private static final Pattern FORMATO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public CorreoElectronico {
        Objects.requireNonNull(valor, "El correo electronico es obligatorio.");
        valor = valor.trim().toLowerCase(java.util.Locale.ROOT);
        if (valor.length() > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException("El correo electronico supera la longitud maxima.");
        }
        if (!FORMATO.matcher(valor).matches()) {
            throw new IllegalArgumentException("El correo electronico no tiene un formato valido.");
        }
    }

    /**
     * Representacion enmascarada, apta para diagnostico.
     *
     * <p>Existe para que registrar un correo en un log sea una decision explicita y no un
     * descuido: {@code toString()} de un record imprime el valor completo, y un correo es
     * dato personal segun la Ley N.o 29733.
     */
    public String enmascarado() {
        int arroba = valor.indexOf('@');
        String local = valor.substring(0, arroba);
        String dominio = valor.substring(arroba);
        if (local.length() <= 2) {
            return "*".repeat(local.length()) + dominio;
        }
        return local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1) + dominio;
    }
}
