package pe.ayni.bank.identity.domain.model;

/**
 * La cuenta esta bloqueada por decision de seguridad o de soporte.
 *
 * <p>No es lo mismo que {@link CuentaBloqueadaException}, y la diferencia importa:
 * aquella es una pausa automatica que se levanta sola en unos minutos; esta no se levanta
 * hasta que alguien intervenga. Por eso el mensaje dirige a soporte en lugar de invitar a
 * reintentar.
 *
 * <p>Se comprueba <strong>despues</strong> de validar la contrasena, nunca antes. Si se
 * comprobara antes, quien probara correos sabria cuales corresponden a cuentas bloqueadas
 * sin necesidad de acertar ni una contrasena, y eso vuelve a ser un oraculo de
 * enumeracion.
 */
public class CuentaInhabilitadaException extends RuntimeException {

    public CuentaInhabilitadaException() {
        super("Tu cuenta esta inhabilitada. Escribenos para revisarla.");
    }
}
