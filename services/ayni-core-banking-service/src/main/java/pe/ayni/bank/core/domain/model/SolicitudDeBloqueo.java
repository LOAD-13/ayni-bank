package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Bloquear la cuenta de un cliente es una operacion sensible y va con el principio de cuatro ojos
 * (HU-15, AYNI-282): un operador la pide y otra persona, un supervisor, la aprueba. Quien la
 * inicio nunca puede aprobarla, aunque tenga los dos permisos.
 *
 * <p>Pendiente mientras no tenga aprobador.
 */
public record SolicitudDeBloqueo(UUID id, UUID cuentaId, String motivo, UUID iniciadaPor, Instant iniciadaEn,
                                 UUID aprobadaPor, Instant aprobadaEn) {

    public static final int MAXIMO_MOTIVO = 200;

    public SolicitudDeBloqueo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cuentaId);
        Objects.requireNonNull(iniciadaPor);
        Objects.requireNonNull(iniciadaEn);
        if (motivo == null || motivo.isBlank()) {
            throw new SolicitudDeBloqueoNoValidaException("Indica el motivo del bloqueo.");
        }
        motivo = motivo.trim();
        if (motivo.length() > MAXIMO_MOTIVO) {
            throw new SolicitudDeBloqueoNoValidaException(
                    "El motivo supera los " + MAXIMO_MOTIVO + " caracteres.");
        }
        if ((aprobadaPor == null) != (aprobadaEn == null)) {
            throw new IllegalArgumentException("Una aprobacion lleva quien y cuando, o ninguno.");
        }
        if (iniciadaPor.equals(aprobadaPor)) {
            throw new CuatroOjosException();
        }
    }

    public static SolicitudDeBloqueo iniciar(UUID id, UUID cuentaId, String motivo, UUID operador, Instant momento) {
        return new SolicitudDeBloqueo(id, cuentaId, motivo, operador, momento, null, null);
    }

    public boolean pendiente() {
        return aprobadaPor == null;
    }

    /**
     * @throws CuatroOjosException si quien aprueba es quien la inicio
     * @throws SolicitudDeBloqueoNoValidaException si ya estaba aprobada
     */
    public SolicitudDeBloqueo aprobar(UUID supervisor, Instant momento) {
        Objects.requireNonNull(supervisor);
        if (!pendiente()) {
            throw new SolicitudDeBloqueoNoValidaException("La solicitud ya fue aprobada.");
        }
        if (supervisor.equals(iniciadaPor)) {
            throw new CuatroOjosException();
        }
        return new SolicitudDeBloqueo(id, cuentaId, motivo, iniciadaPor, iniciadaEn, supervisor, momento);
    }
}
