package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoDeExtraccion;

/** HU-02 · Lee por OCR los datos del DNI a partir de las dos fotos aceptadas. */
public interface ExtraerDatosDelDniUseCase {

    /**
     * @throws pe.ayni.bank.identity.domain.model.SolicitudNoExisteException
     *         si la solicitud no existe o es un senuelo
     * @throws pe.ayni.bank.identity.domain.model.CapturasIncompletasException
     *         si falta la foto aceptada de alguno de los dos lados
     */
    ResultadoDeExtraccion extraer(UUID solicitudId);
}
