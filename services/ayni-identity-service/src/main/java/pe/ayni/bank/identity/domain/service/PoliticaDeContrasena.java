package pe.ayni.bank.identity.domain.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pe.ayni.bank.identity.domain.model.RequisitoDeContrasena;

/**
 * Politica de contrasenas de Ayni Bank, tal como la fija HU-01: minimo doce caracteres,
 * con mayuscula, minuscula, digito y simbolo.
 *
 * <p>Vive en el dominio y no en una anotacion de validacion sobre el DTO porque es una
 * regla de negocio, no un detalle del transporte. Si manana la aplicacion se opera por
 * consola, por un proceso por lotes o por otra API, la regla debe seguir aplicandose sin
 * que nadie la reescriba.
 *
 * <p><strong>Devuelve la lista de incumplimientos en lugar de lanzar una excepcion.</strong>
 * Lanzar al primer fallo obliga a la persona a descubrir los requisitos de uno en uno, a
 * base de intentos. Con la lista completa, la interfaz los muestra todos a la vez.
 */
public final class PoliticaDeContrasena {

    public static final int LONGITUD_MINIMA = 12;

    /**
     * Limite superior. No protege de nada por si mismo, pero Argon2id es deliberadamente
     * costoso: sin tope, una peticion con una contrasena de un megabyte consume CPU del
     * servidor a voluntad del atacante.
     */
    public static final int LONGITUD_MAXIMA = 128;

    private PoliticaDeContrasena() {
        throw new AssertionError("Clase de utilidad; no se instancia.");
    }

    /**
     * @param contrasena contrasena en claro, que <strong>no se registra en ningun log</strong>
     * @return los requisitos incumplidos; lista vacia si la contrasena es valida
     */
    public static List<RequisitoDeContrasena> evaluar(String contrasena) {
        if (contrasena == null || contrasena.isEmpty()) {
            return List.of(RequisitoDeContrasena.values());
        }

        List<RequisitoDeContrasena> incumplidos = new ArrayList<>();

        if (contrasena.length() < LONGITUD_MINIMA) {
            incumplidos.add(RequisitoDeContrasena.LONGITUD_MINIMA);
        }
        if (contrasena.chars().noneMatch(Character::isUpperCase)) {
            incumplidos.add(RequisitoDeContrasena.MAYUSCULA);
        }
        if (contrasena.chars().noneMatch(Character::isLowerCase)) {
            incumplidos.add(RequisitoDeContrasena.MINUSCULA);
        }
        if (contrasena.chars().noneMatch(Character::isDigit)) {
            incumplidos.add(RequisitoDeContrasena.DIGITO);
        }
        // Simbolo es «ni letra ni digito ni espacio». Definirlo por exclusion y no con una
        // lista blanca de caracteres evita rechazar los simbolos que la gente usa de verdad
        // y que ninguna lista escrita a mano recoge entera.
        if (contrasena.chars().noneMatch(PoliticaDeContrasena::esSimbolo)) {
            incumplidos.add(RequisitoDeContrasena.SIMBOLO);
        }

        return Collections.unmodifiableList(incumplidos);
    }

    /** {@code true} si la contrasena cumple la politica completa. */
    public static boolean cumple(String contrasena) {
        return evaluar(contrasena).isEmpty();
    }

    private static boolean esSimbolo(int caracter) {
        return !Character.isLetterOrDigit(caracter) && !Character.isWhitespace(caracter);
    }
}
