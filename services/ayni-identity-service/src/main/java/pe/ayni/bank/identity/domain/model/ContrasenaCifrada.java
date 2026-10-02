package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/**
 * Contrasena ya derivada con Argon2id. Nunca contiene la contrasena en claro.
 *
 * <p>Existe como tipo propio, y no como un {@code String} suelto, para que el compilador
 * impida el error que de verdad ocurre: pasar la contrasena escrita por el usuario donde
 * se esperaba su derivacion. Con dos {@code String} indistinguibles ese fallo se descubre
 * cuando ya hay contrasenas en claro en la base de datos.
 *
 * <p>{@code toString()} esta sobrescrito a proposito: el {@code toString()} que genera un
 * record imprimiria la derivacion completa en cualquier log, traza o mensaje de excepcion.
 * La derivacion no es la contrasena, pero es material para un ataque de diccionario fuera
 * de linea y no tiene por que aparecer en un fichero de texto.
 */
public record ContrasenaCifrada(String valor) {

    public ContrasenaCifrada {
        Objects.requireNonNull(valor, "La contrasena cifrada es obligatoria.");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("La contrasena cifrada no puede estar vacia.");
        }
        if (!valor.startsWith("$argon2")) {
            throw new IllegalArgumentException(
                    "La contrasena debe estar derivada con Argon2id.");
        }
    }

    @Override
    public String toString() {
        return "ContrasenaCifrada[oculta]";
    }
}
