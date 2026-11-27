package pe.ayni.bank.core.domain.port.out;

import java.util.List;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.VersionDeCuenta;

/** Las versiones guardadas de una cuenta, de la primera a la ultima (HU-15, Envers). */
public interface HistorialDeCuentasPort {

    List<VersionDeCuenta> versionesDe(UUID cuentaId);
}
