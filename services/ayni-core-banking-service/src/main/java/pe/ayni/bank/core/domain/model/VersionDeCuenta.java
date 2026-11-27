package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una version de la cuenta en su historial (HU-15, AYNI-280): como estaba tras un cambio, cuando
 * ocurrio y quien lo hizo. El actor es nulo en la siembra inicial y en los procesos programados.
 */
public record VersionDeCuenta(long revision, Instant momento, UUID actor, Tipo tipo,
                              String numero, EstadoCuenta estado, Moneda moneda) {

    /** Que paso en esa revision. */
    public enum Tipo { ALTA, CAMBIO, BAJA }

    public VersionDeCuenta {
        Objects.requireNonNull(momento);
        Objects.requireNonNull(tipo);
    }
}
