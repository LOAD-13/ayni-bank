package pe.ayni.bank.core.domain.port.out;

import java.util.UUID;

import pe.ayni.bank.core.domain.model.OperacionAConfirmar;

/**
 * Comprueba que el titular confirmo con su segundo factor exactamente esta operacion
 * (HU-07). El dominio no sabe que el comprobante es un JWT firmado por identity: solo que
 * acredita a un usuario y a una operacion durante unos minutos. Ver ADR-0031.
 */
public interface VerificadorDeConfirmacionPort {

    /**
     * @param confirmacion el comprobante que entrego identity; puede ser nulo
     * @throws pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException si falta, caduco,
     *         esta mal firmado, es de otro usuario o confirma otra operacion
     */
    void verificar(String confirmacion, UUID usuarioId, OperacionAConfirmar operacion);
}
