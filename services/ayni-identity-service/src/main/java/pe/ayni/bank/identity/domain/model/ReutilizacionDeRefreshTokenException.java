package pe.ayni.bank.identity.domain.model;

import java.util.UUID;

/**
 * Alguien presento un token de renovacion que ya se habia gastado.
 *
 * <p>En un uso normal esto no ocurre: cada token se usa exactamente una vez. Que ocurra
 * significa que existe una copia, y la respuesta no es rechazar la peticion sino invalidar
 * la familia entera. Ver escenario 4 de HU-04.
 *
 * <p>Lleva el identificador de la familia porque quien la maneja necesita saber cual
 * invalidar. No lleva el token: seguiria siendo un secreto valido para alguien.
 */
public class ReutilizacionDeRefreshTokenException extends RuntimeException {

    private final transient UUID familiaId;

    public ReutilizacionDeRefreshTokenException(UUID familiaId) {
        super("Se reutilizo un token de renovacion ya consumido.");
        this.familiaId = familiaId;
    }

    public UUID familiaId() {
        return familiaId;
    }
}
