package pe.ayni.bank.core.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;

/**
 * Donde viven las tarjetas · HU-09. Quien lo implemente guarda el PAN cifrado y nunca en
 * claro; el dominio no sabe como.
 */
public interface RepositorioDeTarjetasPort {

    Optional<Tarjeta> deTitular(UUID usuarioId);

    /** Si ya hay una tarjeta con ese PAN; la emision genera otro si choca. */
    boolean existePan(Pan pan);

    void guardar(Tarjeta tarjeta);
}
