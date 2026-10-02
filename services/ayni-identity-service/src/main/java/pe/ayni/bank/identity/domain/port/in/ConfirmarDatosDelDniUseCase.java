package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.DatosConfirmados;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;

/**
 * HU-02 · El titular revisa lo que leyo el OCR, corrige lo que haga falta y confirma.
 *
 * <p>Lo confirmado se contrasta con lo declarado en el paso 1 (ADR-0009). Si coincide, la
 * solicitud pasa a DOCUMENTO_CARGADO; si no, a revision manual.
 */
public interface ConfirmarDatosDelDniUseCase {

    /**
     * @return {@code ACEPTADO} o {@code EN_REVISION_MANUAL}
     * @throws pe.ayni.bank.identity.domain.model.SolicitudNoExisteException
     *         si la solicitud no existe o es un senuelo
     * @throws pe.ayni.bank.identity.domain.model.CapturasIncompletasException
     *         si todavia no hay una lectura del OCR que confirmar
     * @throws IllegalArgumentException si los datos no son validos (numero sin ocho
     *         digitos, sexo distinto de M o F...)
     */
    EstadoDelPasoKyc confirmar(UUID solicitudId, DatosConfirmados confirmados);
}
