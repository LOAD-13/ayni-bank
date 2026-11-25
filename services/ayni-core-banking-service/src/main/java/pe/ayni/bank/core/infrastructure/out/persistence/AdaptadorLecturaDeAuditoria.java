package pe.ayni.bank.core.infrastructure.out.persistence;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.ayni.bank.core.domain.model.EventoDeAuditoria;
import pe.ayni.bank.core.domain.model.FiltroDeAuditoria;
import pe.ayni.bank.core.domain.model.PaginaDeAuditoria;
import pe.ayni.bank.core.domain.port.out.LecturaDeAuditoriaPort;

/**
 * Implementa {@link LecturaDeAuditoriaPort} con SQL de solo lectura. El filtro se compone con
 * condiciones fijas y todo valor viaja como parametro, nunca pegado al texto.
 */
@Repository
public class AdaptadorLecturaDeAuditoria implements LecturaDeAuditoriaPort {

    private final JdbcTemplate jdbc;

    AdaptadorLecturaDeAuditoria(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PaginaDeAuditoria buscar(FiltroDeAuditoria filtro, int pagina, int tamano) {
        StringBuilder donde = new StringBuilder(" WHERE TRUE");
        List<Object> parametros = new ArrayList<>();
        if (filtro.usuarioId() != null) {
            donde.append(" AND usuario_id = ?");
            parametros.add(filtro.usuarioId());
        }
        if (filtro.tipo() != null) {
            donde.append(" AND tipo = ?");
            parametros.add(filtro.tipo());
        }
        if (filtro.desde() != null) {
            donde.append(" AND ocurrido_en >= ?");
            parametros.add(Timestamp.from(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            donde.append(" AND ocurrido_en < ?");
            parametros.add(Timestamp.from(filtro.hasta()));
        }

        Long total = jdbc.queryForObject("SELECT count(*) FROM core.evento_auditoria" + donde,
                Long.class, parametros.toArray());

        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(tamano);
        conPagina.add((long) pagina * tamano);
        List<EventoDeAuditoria> eventos = jdbc.query("""
                SELECT id, tipo, usuario_id, movimiento_id, motivo, resultado, recurso, ip, agente, ocurrido_en
                  FROM core.evento_auditoria""" + donde + " ORDER BY ocurrido_en DESC, id DESC LIMIT ? OFFSET ?",
                (fila, n) -> new EventoDeAuditoria(fila.getLong("id"), fila.getString("tipo"),
                        fila.getObject("usuario_id", UUID.class), fila.getObject("movimiento_id", UUID.class),
                        fila.getString("motivo"), fila.getString("resultado"), fila.getString("recurso"),
                        fila.getString("ip"), fila.getString("agente"),
                        fila.getTimestamp("ocurrido_en").toInstant()),
                conPagina.toArray());

        return new PaginaDeAuditoria(eventos, pagina, tamano, total == null ? 0 : total);
    }
}
