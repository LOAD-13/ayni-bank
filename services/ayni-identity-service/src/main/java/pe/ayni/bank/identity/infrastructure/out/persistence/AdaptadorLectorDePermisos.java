package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.identity.domain.port.out.LectorDePermisosPort;

/** Implementa {@link LectorDePermisosPort} sobre {@code usuario_rol}, {@code rol_permiso} y {@code permiso}. */
@Repository
public class AdaptadorLectorDePermisos implements LectorDePermisosPort {

    private final JdbcTemplate jdbc;

    AdaptadorLectorDePermisos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> permisosDe(UUID usuarioId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT p.codigo
                  FROM identity.usuario_rol ur
                  JOIN identity.rol_permiso rp ON rp.rol_id = ur.rol_id
                  JOIN identity.permiso p ON p.id = rp.permiso_id
                 WHERE ur.usuario_id = ?
                 ORDER BY p.codigo
                """, String.class, usuarioId));
    }
}
