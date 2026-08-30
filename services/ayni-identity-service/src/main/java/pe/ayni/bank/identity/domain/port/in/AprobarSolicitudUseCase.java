package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.SolicitudNoAprobableException;

/**
 * Marca una solicitud de onboarding como aprobada, que es lo que dispara la apertura de la
 * cuenta de ahorro en core-banking.
 *
 * <p>En el Sprint 2 lo invocara el resultado del KYC. En el Sprint 1 lo invoca un endpoint
 * de perfil {@code dev}, porque sin OCR no hay nada que apruebe nada y sin aprobacion no
 * se puede demostrar que la cadena entera funciona.
 */
public interface AprobarSolicitudUseCase {

    /** @throws SolicitudNoAprobableException si la solicitud no existe o es un senuelo */
    void aprobar(UUID solicitudId);
}
