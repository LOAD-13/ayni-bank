package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;

/**
 * Decide que hacer con una solicitud cuando un intento de verificacion KYC no prospera.
 *
 * <p>Distingue dos motivos de fallo porque no significan lo mismo (ver ADR-0021):
 *
 * <ul>
 *   <li>{@link #registrarFalloDeUsuario(UUID)} — el documento no paso la verificacion
 *       (rechazado por {@code kyc-service}, datos declarados que no coinciden con el OCR,
 *       etc.). Es algo que el usuario puede corregir reintentando, asi que cuenta contra el
 *       limite de tres intentos.
 *   <li>{@link #derivarPorServicioNoDisponible(UUID)} — {@code kyc-service} no respondio tras
 *       agotar Resilience4j ({@code KycServiceNoDisponibleException}, subtarea 10). No es un
 *       problema que reintentar resuelva, asi que deriva de inmediato sin gastar intentos.
 * </ul>
 */
public interface GestionarFalloDeVerificacionKycUseCase {

    /** @return si la solicitud aun puede reintentar o si ya se derivo a revision manual */
    ResultadoDelIntentoKyc registrarFalloDeUsuario(UUID solicitudId);

    /** Deriva la solicitud a revision manual sin consumir intentos del usuario. */
    void derivarPorServicioNoDisponible(UUID solicitudId);
}
