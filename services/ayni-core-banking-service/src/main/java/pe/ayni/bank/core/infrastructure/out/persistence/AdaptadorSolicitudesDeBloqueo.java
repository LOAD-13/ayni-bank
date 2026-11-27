package pe.ayni.bank.core.infrastructure.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException;
import pe.ayni.bank.core.domain.port.out.RepositorioDeSolicitudesDeBloqueoPort;

/**
 * Solicitudes de bloqueo sobre {@code core.solicitud_bloqueo} (HU-15, AYNI-282). Con JDBC y no JPA
 * para que la aprobacion sea un UPDATE condicionado a que siga pendiente: de dos supervisores que
 * aprueban a la vez, solo uno cambia la fila.
 */
@Repository
public class AdaptadorSolicitudesDeBloqueo implements RepositorioDeSolicitudesDeBloqueoPort {

    private static final String COLUMNAS =
            "id, cuenta_id, motivo, iniciada_por, iniciada_en, aprobada_por, aprobada_en";

    private final JdbcTemplate jdbc;

    AdaptadorSolicitudesDeBloqueo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(SolicitudDeBloqueo s) {
        jdbc.update("INSERT INTO core.solicitud_bloqueo (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, NULL, NULL)",
                s.id(), s.cuentaId(), s.motivo(), s.iniciadaPor(), Timestamp.from(s.iniciadaEn()));
    }

    @Override
    public void aprobar(SolicitudDeBloqueo s) {
        int filas = jdbc.update("""
                UPDATE core.solicitud_bloqueo SET aprobada_por = ?, aprobada_en = ?
                WHERE id = ? AND aprobada_por IS NULL""",
                s.aprobadaPor(), Timestamp.from(s.aprobadaEn()), s.id());
        if (filas == 0) {
            throw new SolicitudDeBloqueoNoValidaException("La solicitud ya fue aprobada.");
        }
    }

    @Override
    public Optional<SolicitudDeBloqueo> buscar(UUID id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM core.solicitud_bloqueo WHERE id = ?",
                AdaptadorSolicitudesDeBloqueo::fila, id).stream().findFirst();
    }

    @Override
    public boolean existePendientePara(UUID cuentaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM core.solicitud_bloqueo
                               WHERE cuenta_id = ? AND aprobada_por IS NULL)""", Boolean.class, cuentaId));
    }

    @Override
    public List<SolicitudDeBloqueo> pendientes() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM core.solicitud_bloqueo WHERE aprobada_por IS NULL"
                + " ORDER BY iniciada_en", AdaptadorSolicitudesDeBloqueo::fila);
    }

    private static SolicitudDeBloqueo fila(ResultSet rs, int n) throws SQLException {
        Timestamp aprobadaEn = rs.getTimestamp("aprobada_en");
        return new SolicitudDeBloqueo(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class),
                rs.getString("motivo"), rs.getObject("iniciada_por", UUID.class),
                rs.getTimestamp("iniciada_en").toInstant(), rs.getObject("aprobada_por", UUID.class),
                aprobadaEn == null ? null : aprobadaEn.toInstant());
    }
}
