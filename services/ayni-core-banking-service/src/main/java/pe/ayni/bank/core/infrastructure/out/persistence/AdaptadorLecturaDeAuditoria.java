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
 * condiciones fijas y cada valor viaja como parametro, nunca pegado al texto.
 */
@Repository
public class AdaptadorLecturaDeAuditoria implements LecturaDeAuditoriaPort {

    private final JdbcTemplate jdbc;

    AdaptadorLecturaDeAuditoria(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Un filtro nulo deja pasar todo: el texto del SQL es siempre el mismo y cada valor va dos veces.
    private static final String DONDE = """
             WHERE (CAST(? AS uuid) IS NULL OR usuario_id = ?)
               AND (CAST(? AS varchar) IS NULL OR tipo = ?)
               AND (CAST(? AS timestamptz) IS NULL OR ocurrido_en >= ?)
               AND (CAST(? AS timestamptz) IS NULL OR ocurrido_en < ?)""";

    private static final String CONTAR = "SELECT count(*) FROM core.evento_auditoria " + DONDE;

    private static final String LISTAR = """
            SELECT id, tipo, usuario_id, movimiento_id, motivo, resultado, recurso, ip, agente, ocurrido_en
              FROM core.evento_auditoria""" + " " + DONDE + " ORDER BY ocurrido_en DESC, id DESC LIMIT ? OFFSET ?";

    @Override
    public PaginaDeAuditoria buscar(FiltroDeAuditoria filtro, int pagina, int tamano) {
        Timestamp desde = filtro.desde() == null ? null : Timestamp.from(filtro.desde());
        Timestamp hasta = filtro.hasta() == null ? null : Timestamp.from(filtro.hasta());
        List<Object> parametros = new ArrayList<>();
        for (Object valor : new Object[] {filtro.usuarioId(), filtro.tipo(), desde, hasta}) {
            parametros.add(valor);
            parametros.add(valor);
        }

        Long total = jdbc.queryForObject(CONTAR, Long.class, parametros.toArray());

        List<Object> conPagina = new ArrayList<>(parametros);
        conPagina.add(tamano);
        conPagina.add((long) pagina * tamano);
        List<EventoDeAuditoria> eventos = jdbc.query(LISTAR,
                (fila, n) -> new EventoDeAuditoria(fila.getLong("id"), fila.getString("tipo"),
                        fila.getObject("usuario_id", UUID.class), fila.getObject("movimiento_id", UUID.class),
                        fila.getString("motivo"), fila.getString("resultado"), fila.getString("recurso"),
                        fila.getString("ip"), fila.getString("agente"),
                        fila.getTimestamp("ocurrido_en").toInstant()),
                conPagina.toArray());

        return new PaginaDeAuditoria(eventos, pagina, tamano, total == null ? 0 : total);
    }
}
