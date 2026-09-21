package pe.ayni.bank.identity.domain.model;

/**
 * Se lanza cuando un desafío por código (OTP) ha superado el máximo de 3 intentos fallidos.
 */
public class MaximoIntentosDesafioExcedidoException extends RuntimeException {

    public MaximoIntentosDesafioExcedidoException() {
        super("Se ha superado el número máximo de intentos permitidos (3). Solicita un nuevo código.");
    }
}
