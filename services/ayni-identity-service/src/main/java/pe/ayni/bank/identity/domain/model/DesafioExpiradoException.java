package pe.ayni.bank.identity.domain.model;

/**
 * Se lanza cuando un desafío por código (OTP) ha superado su tiempo de expiración (10 min).
 */
public class DesafioExpiradoException extends RuntimeException {

    public DesafioExpiradoException() {
        super("El código de verificación ha expirado. Solicita un nuevo código.");
    }
}
