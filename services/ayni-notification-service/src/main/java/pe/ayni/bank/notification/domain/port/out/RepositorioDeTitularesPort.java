package pe.ayni.bank.notification.domain.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.notification.domain.model.Titular;

public interface RepositorioDeTitularesPort {

    /** Inserta o reemplaza: el ultimo evento manda. */
    void guardar(Titular titular, Instant actualizadoEn);

    Optional<Titular> buscar(UUID usuarioId);
}
