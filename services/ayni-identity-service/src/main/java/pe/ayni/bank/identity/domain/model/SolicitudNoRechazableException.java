package pe.ayni.bank.identity.domain.model;

/**
 * La solicitud no se puede rechazar.
 *
 * <p>{@code existe} distingue los dos casos porque se responden distinto: una solicitud que
 * no existe o que es un senuelo sin titular (ADR-0008) se trata como inexistente, igual que
 * al aprobar; una que ya se cerro (aprobada, rechazada o vencida) existe, pero su resultado
 * ya no se puede cambiar.
 */
public class SolicitudNoRechazableException extends RuntimeException {

    private final boolean existe;

    private SolicitudNoRechazableException(String motivo, boolean existe) {
        super(motivo);
        this.existe = existe;
    }

    public static SolicitudNoRechazableException inexistente() {
        return new SolicitudNoRechazableException("La solicitud no existe o es un senuelo sin titular.", false);
    }

    public static SolicitudNoRechazableException cerrada(String estado) {
        return new SolicitudNoRechazableException("La solicitud ya esta " + estado + ": no se puede rechazar.", true);
    }

    public boolean existe() {
        return existe;
    }
}
