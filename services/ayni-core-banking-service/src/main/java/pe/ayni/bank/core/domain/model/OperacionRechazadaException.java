package pe.ayni.bank.core.domain.model;

/** Una regla de negocio impide la operacion. No es un error del sistema. */
public class OperacionRechazadaException extends RuntimeException {

    private final MotivoDeRechazo motivo;

    public OperacionRechazadaException(MotivoDeRechazo motivo) {
        super(motivo.detalle());
        this.motivo = motivo;
    }

    public MotivoDeRechazo motivo() {
        return motivo;
    }
}
