package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;

public interface GenerarUrlDeSubidaUseCase {

    /**
     * @throws pe.ayni.bank.identity.domain.model.SolicitudNoExisteException
     *         si la solicitud no existe o es un senuelo sin titular
     */
    UrlDeSubida generar(UUID solicitudId, TipoDeDocumentoKyc tipoDocumento, String extension);
}
