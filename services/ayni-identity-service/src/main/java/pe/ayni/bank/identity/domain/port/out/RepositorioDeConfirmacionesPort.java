package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.ConfirmacionDeOperacion;

/** Persistencia de las confirmaciones de operaciones (HU-07). */
public interface RepositorioDeConfirmacionesPort {

    void guardar(ConfirmacionDeOperacion confirmacion);

    Optional<ConfirmacionDeOperacion> buscar(UUID id);
}
