package pe.ayni.bank.identity.domain.model;

/**
 * La solicitud no existe, o es un senuelo sin titular (ADR-0008).
 *
 * <p>Se usa donde una operacion necesita una solicitud real detras (por
 * ejemplo, generar una URL de subida para un documento KYC): un senuelo no
 * tiene usuario, asi que no hay a que expediente asociar el documento.
 */
public class SolicitudNoExisteException extends RuntimeException {

    public SolicitudNoExisteException(String motivo) {
        super(motivo);
    }
}
