package pe.ayni.bank.core.domain.model;

/**
 * La transferencia no viene confirmada con el segundo factor, o la confirmacion no es de
 * esta operacion. Un solo mensaje para todos los casos: detallar si caduco o si la firma no
 * cuadra solo orienta a quien esta probando a falsificarla.
 */
public class ConfirmacionInvalidaException extends RuntimeException {

    public ConfirmacionInvalidaException() {
        super("Confirma la transferencia con tu segundo factor.");
    }
}
