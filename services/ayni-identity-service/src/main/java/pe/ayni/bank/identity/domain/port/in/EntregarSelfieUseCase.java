package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;

/**
 * HU-03 · el solicitante entrego su selfie (ya subida al almacen con la URL firmada).
 *
 * <p>Se registra como documento KYC y la solicitud pasa a un operador, que compara la
 * selfie con la foto del DNI y aprueba la apertura de la cuenta.
 */
public interface EntregarSelfieUseCase {

    EstadoDelPasoKyc entregar(UUID solicitudId, String claveDeObjeto);
}
