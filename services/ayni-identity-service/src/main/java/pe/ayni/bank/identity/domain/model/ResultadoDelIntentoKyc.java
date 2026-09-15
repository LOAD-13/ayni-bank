package pe.ayni.bank.identity.domain.model;

/**
 * Que pasa con la solicitud despues de registrar un intento de verificacion fallido.
 *
 * <p>Ver ADR-0021: al tercer fallo la solicitud se deriva a revision manual en vez de
 * dejar que el usuario siga intentando indefinidamente.
 */
public enum ResultadoDelIntentoKyc {
    PUEDE_REINTENTAR,
    DERIVADA_A_REVISION_MANUAL
}
