package pe.ayni.bank.identity.domain.port.out;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoDeVerificacionKyc;

/**
 * Inicia una verificacion de identidad en kyc-service (Python). El dominio no
 * conoce el cliente OpenAPI generado ni Resilience4j — ver diseno-base.md §3.4-3.5.
 */
public interface VerificadorKycPort {

    /**
     * @param anversoDocumentoId identificador del documento del anverso (documento_kyc.id)
     * @param reversoDocumentoId identificador del documento del reverso (documento_kyc.id)
     * @throws pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException
     *         si se agotan los reintentos o el circuito esta abierto
     */
    ResultadoDeVerificacionKyc iniciar(UUID anversoDocumentoId, UUID reversoDocumentoId);
}
