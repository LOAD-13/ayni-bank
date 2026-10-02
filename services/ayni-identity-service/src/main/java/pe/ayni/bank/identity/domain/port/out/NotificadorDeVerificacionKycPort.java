package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.CorreoElectronico;

/** Avisos que salen de la verificacion de identidad (HU-02, AYNI-13). */
public interface NotificadorDeVerificacionKycPort {

    /**
     * La solicitud se derivo a revision manual: por agotar el limite de intentos
     * ({@link pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc#DERIVADA_A_REVISION_MANUAL})
     * o porque kyc-service no respondio. El motivo no cambia el aviso: en los dos casos el
     * solicitante necesita enterarse de que un operador revisara su caso.
     */
    void avisarEnRevisionManual(CorreoElectronico correo);
}
