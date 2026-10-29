package pe.ayni.bank.identity.domain.model;

/**
 * Por que un operador rechaza una solicitud de onboarding tras revisarla a mano (HU-05,
 * escenario 3).
 *
 * <p>Es una lista cerrada y no un texto libre a proposito: el motivo le llega al cliente por
 * correo, y un texto escrito en el momento podria filtrar detalles internos de la revision o
 * no decirle que puede hacer. Cada motivo lleva el texto que vera el cliente.
 */
public enum MotivoDeRechazoDeSolicitud {

    SELFIE_NO_COINCIDE("La foto de tu rostro no coincide con la de tu DNI."),
    DOCUMENTO_NO_VALIDO("No pudimos validar tu DNI con las fotos que enviaste."),
    DATOS_NO_COINCIDEN("Los datos que declaraste no coinciden con los de tu DNI.");

    private final String detalle;

    MotivoDeRechazoDeSolicitud(String detalle) {
        this.detalle = detalle;
    }

    public String detalle() {
        return detalle;
    }
}
