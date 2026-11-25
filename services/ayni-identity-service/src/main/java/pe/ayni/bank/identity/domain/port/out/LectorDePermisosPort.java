package pe.ayni.bank.identity.domain.port.out;

import java.util.Set;
import java.util.UUID;

/**
 * Los permisos que le dan a un usuario sus roles (AYNI-281). El token de acceso los lleva
 * para que el gateway decida por ruta sin preguntar a identity en cada peticion.
 */
public interface LectorDePermisosPort {

    /** Codigos {@code recurso:accion}, sin repetidos. Vacio si el usuario no tiene ningun rol. */
    Set<String> permisosDe(UUID usuarioId);
}
