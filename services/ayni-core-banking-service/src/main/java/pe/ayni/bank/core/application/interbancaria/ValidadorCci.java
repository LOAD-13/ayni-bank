package pe.ayni.bank.core.application.interbancaria;

import pe.ayni.bank.core.domain.model.Cci;

/**
 * Valida los CCI utilizando las reglas
 * existentes en el dominio de Ayni Bank.
 */
public final class ValidadorCci {

    private ValidadorCci() {
        // Clase de utilidad: no se instancia.
    }

    public static void validar(String cci) {
        new Cci(cci);
    }

    public static boolean esValido(String cci) {
        try {
            validar(cci);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
