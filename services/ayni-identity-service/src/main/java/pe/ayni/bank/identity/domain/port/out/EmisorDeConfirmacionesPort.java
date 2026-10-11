package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;

/**
 * Firma el token de confirmacion que core-banking exige para transferir (ADR-0031). El
 * dominio no sabe que es un JWT: sabe que acredita a un usuario y a una operacion durante
 * unos minutos.
 */
public interface EmisorDeConfirmacionesPort {

    String emitir(ConfirmacionDeOperacion confirmacion);
}
