package pe.ayni.bank.identity.domain.model;

/**
 * Se intento llevar un usuario a un estado al que no puede llegar desde el actual.
 *
 * <p>Que exista esta excepcion es el punto: sin ella, activar dos veces al mismo usuario o
 * reactivar a uno bloqueado seria una operacion silenciosa que nadie detecta.
 */
public class TransicionDeEstadoInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient EstadoUsuario origen;
    private final transient EstadoUsuario destino;

    public TransicionDeEstadoInvalidaException(EstadoUsuario origen, EstadoUsuario destino) {
        super("No se puede pasar de " + origen + " a " + destino + ".");
        this.origen = origen;
        this.destino = destino;
    }

    public EstadoUsuario origen() {
        return origen;
    }

    public EstadoUsuario destino() {
        return destino;
    }
}
