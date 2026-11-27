package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * El historial de una cuenta y la version que tenia en una fecha (HU-15, escenario 2). Antes de la
 * primera version no hay estado conocido: el historial empieza cuando se activo Envers.
 */
public record HistorialDeCuenta(Instant fecha, Optional<VersionDeCuenta> vigente, List<VersionDeCuenta> versiones) {

    public HistorialDeCuenta {
        versiones = versiones.stream().sorted(Comparator.comparingLong(VersionDeCuenta::revision)).toList();
    }

    /** La ultima version registrada hasta {@code fecha}, incluida. */
    public static HistorialDeCuenta en(Instant fecha, List<VersionDeCuenta> versiones) {
        Optional<VersionDeCuenta> vigente = versiones.stream()
                .filter(v -> !v.momento().isAfter(fecha))
                .max(Comparator.comparingLong(VersionDeCuenta::revision));
        return new HistorialDeCuenta(fecha, vigente, versiones);
    }
}
