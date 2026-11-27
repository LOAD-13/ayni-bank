package pe.ayni.bank.core.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;

/** Las solicitudes de bloqueo de cuenta y su aprobacion (HU-15, AYNI-282). */
public interface RepositorioDeSolicitudesDeBloqueoPort {

    void registrar(SolicitudDeBloqueo nueva);

    /**
     * Guarda la aprobacion solo si la solicitud sigue pendiente: de dos aprobaciones simultaneas,
     * gana una.
     *
     * @throws pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException si otra persona
     *         ya la aprobo
     */
    void aprobar(SolicitudDeBloqueo aprobada);

    Optional<SolicitudDeBloqueo> buscar(UUID id);

    boolean existePendientePara(UUID cuentaId);

    /** Las pendientes, de la mas antigua a la mas reciente. */
    List<SolicitudDeBloqueo> pendientes();
}
