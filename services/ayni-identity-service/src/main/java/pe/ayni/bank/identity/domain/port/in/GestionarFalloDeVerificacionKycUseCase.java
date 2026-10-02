package pe.ayni.bank.identity.domain.port.in;

import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ResultadoDelIntentoKyc;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/**
 * Decide que hacer con una solicitud cuando un paso de la verificacion KYC no prospera.
 *
 * <p>Distingue los motivos de fallo porque no significan lo mismo (ver ADR-0021 y ADR-0028):
 *
 * <ul>
 *   <li>{@link #registrarFalloDeUsuario(UUID, TipoDeDocumentoKyc)} — la foto de un lado no
 *       paso la verificacion (no es un DNI, calidad insuficiente, no se pudo leer). Es algo
 *       que el usuario puede corregir repitiendo la foto, asi que cuenta contra el limite de
 *       tres intentos de ese lado.
 *   <li>{@link #derivarPorServicioNoDisponible(UUID)} — {@code kyc-service} no respondio tras
 *       agotar Resilience4j. Repetir no depende del usuario: deriva sin gastar intentos.
 *   <li>{@link #derivarPorDiscrepancia(UUID)} — lo leido del DNI no coincide con lo declarado,
 *       o el documento cambio despues de evaluarlo. Repetir no lo arregla: lo revisa un
 *       operador.
 * </ul>
 */
public interface GestionarFalloDeVerificacionKycUseCase {

    /** @return si aun puede repetir la foto de ese lado, y cuantas veces, o si ya se derivo */
    ResultadoDelIntentoKyc registrarFalloDeUsuario(UUID solicitudId, TipoDeDocumentoKyc lado);

    /** Deriva la solicitud a revision manual sin consumir intentos del usuario. */
    void derivarPorServicioNoDisponible(UUID solicitudId);

    /** Deriva la solicitud a revision manual porque el documento no cuadra con lo declarado. */
    void derivarPorDiscrepancia(UUID solicitudId);
}
