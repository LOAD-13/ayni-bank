package pe.ayni.bank.identity.domain.model;

/**
 * HU-22 (AYNI-130) · Se intento avanzar a la verificacion documental (KYC) sin haber
 * verificado antes el segundo factor de autenticacion elegido en el registro.
 */
public class SegundoFactorNoVerificadoException extends RuntimeException {

    public SegundoFactorNoVerificadoException() {
        super("Debes verificar tu segundo factor antes de continuar con la captura de tu documento.");
    }
}
