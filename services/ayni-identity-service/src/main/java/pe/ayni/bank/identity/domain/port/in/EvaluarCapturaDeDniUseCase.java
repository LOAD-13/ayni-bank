package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoDeCaptura;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/**
 * HU-02 · Evalua la foto de un lado del DNI que el navegador acaba de subir.
 *
 * <p>Si se acepta, queda registrada en {@code documento_kyc} con su hash. Si se rechaza, se
 * borra del almacen, cuenta un intento de ese lado y se devuelve el motivo concreto.
 */
public interface EvaluarCapturaDeDniUseCase {

    /**
     * @throws pe.ayni.bank.identity.domain.model.SolicitudNoExisteException
     *         si la solicitud no existe o es un senuelo
     * @throws pe.ayni.bank.identity.domain.model.DocumentoNoSubidoException
     *         si la clave no es una foto subida de ese lado para esa solicitud
     */
    ResultadoDeCaptura evaluar(UUID solicitudId, TipoDeDocumentoKyc lado, String claveDeObjeto);
}
