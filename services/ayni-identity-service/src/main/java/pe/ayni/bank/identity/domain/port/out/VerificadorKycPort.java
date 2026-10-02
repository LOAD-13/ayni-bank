package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;

import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/**
 * Verificacion del DNI en kyc-service (Python). El dominio no conoce el cliente OpenAPI
 * generado ni Resilience4j — ver diseno-base.md §3.4-3.5 y ADR-0026.
 *
 * <p>Ambas operaciones lanzan {@link pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException}
 * si se agotan los reintentos o el circuito esta abierto.
 */
public interface VerificadorKycPort {

    /** Si la foto es un DNI y tiene calidad suficiente; si no, el motivo concreto. */
    EvaluacionDeCaptura evaluar(String claveDeObjeto, TipoDeDocumentoKyc lado);

    /** Los datos del DNI leidos por OCR; vacio si no se pudieron leer. */
    Optional<LecturaDelDni> extraer(String claveAnverso, String claveReverso);
}
