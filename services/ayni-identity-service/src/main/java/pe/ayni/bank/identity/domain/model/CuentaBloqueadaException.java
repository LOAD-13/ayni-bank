package pe.ayni.bank.identity.domain.model;

import java.time.Duration;

/**
 * El ingreso esta pausado por acumular intentos fallidos.
 *
 * <p>Lleva la espera restante porque la pantalla aprobada muestra una cuenta atras. Decir
 * cuanto falta no debilita nada: quien esta bloqueado ya sabe que lo esta, y ocultarselo
 * solo consigue que reintente a ciegas.
 */
public class CuentaBloqueadaException extends RuntimeException {

    private final transient Duration esperaRestante;

    public CuentaBloqueadaException(Duration esperaRestante) {
        super("El ingreso esta pausado por seguridad. Intentalo de nuevo en unos minutos.");
        this.esperaRestante = esperaRestante;
    }

    public Duration esperaRestante() {
        return esperaRestante;
    }
}
