package pe.ayni.bank.core.domain.model;

/** La solicitud de bloqueo no se puede crear o aprobar en el estado actual (HU-15, AYNI-282). */
public class SolicitudDeBloqueoNoValidaException extends RuntimeException {

    public SolicitudDeBloqueoNoValidaException(String detalle) {
        super(detalle);
    }
}
