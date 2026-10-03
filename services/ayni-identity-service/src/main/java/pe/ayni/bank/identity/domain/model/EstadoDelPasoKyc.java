package pe.ayni.bank.identity.domain.model;

/**
 * Como termina un paso de la verificacion del DNI, visto desde el solicitante.
 *
 * <ul>
 *   <li>{@code ACEPTADO}: puede seguir al paso siguiente.
 *   <li>{@code RECHAZADO}: debe repetir la foto (o las fotos, si no se pudieron leer).
 *   <li>{@code EN_REVISION_MANUAL}: un operador revisara el caso (escenario 4, o una
 *       discrepancia entre lo declarado y el documento).
 *   <li>{@code VERIFICACION_DIFERIDA}: kyc-service no respondio; la solicitud queda en
 *       revision y el solicitante no ve un error tecnico (escenario 5).
 * </ul>
 */
public enum EstadoDelPasoKyc {
    ACEPTADO,
    RECHAZADO,
    EN_REVISION_MANUAL,
    VERIFICACION_DIFERIDA
}
