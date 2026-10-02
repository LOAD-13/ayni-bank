package pe.ayni.bank.identity.domain.model;

/**
 * La solicitud no existe, ya se resolvio, o no tiene titular detras.
 *
 * <p>El ultimo caso es el importante: las solicitudes senuelo que devuelve el registro ante
 * un correo ya existente no apuntan a ningun usuario (ADR-0008). Aprobar una de ellas
 * abriria una cuenta de ahorro que no pertenece a nadie.
 */
public class SolicitudNoAprobableException extends RuntimeException {

    public SolicitudNoAprobableException(String motivo) {
        super(motivo);
    }
}
