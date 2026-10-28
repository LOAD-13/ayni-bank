
package pe.ayni.bank.core.interbancaria;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConciliadorInterbancario {

    private static final Logger log =
        LoggerFactory.getLogger(
            ConciliadorInterbancario.class
        );

    private final JdbcTemplate db;
    private final CamaraPort camara;
    private final ResolucionInterbancariaService resolucion;

    public ConciliadorInterbancario(
        JdbcTemplate db,
        CamaraPort camara,
        ResolucionInterbancariaService resolucion
    ) {
        this.db = db;
        this.camara = camara;
        this.resolucion = resolucion;
    }

    @Scheduled(
        fixedDelayString =
            "${ayni.camara.conciliacion-ms:15000}"
    )
    public void conciliar() {

        List<UUID> pendientes = db.query(
            """
            SELECT id
            FROM transferencia_interbancaria
            WHERE estado = 'PENDIENTE'
            ORDER BY vence_en
            LIMIT 50
            """,
            (rs, n) ->
                UUID.fromString(rs.getString("id"))
        );

        for (UUID id : pendientes) {
            try {
                EstadoInterbancario estado =
                    camara.consultar(id);

                if (estado == EstadoInterbancario.COMPLETADA
                    || estado == EstadoInterbancario.RECHAZADA) {

                    resolucion.resolver(id, estado);
                }

            } catch (RuntimeException ex) {
                // No revertir ante una respuesta desconocida.
                log.warn(
                    "Transferencia {} pendiente de conciliacion: {}",
                    id,
                    ex.getMessage()
                );
            }
        }
    }
}
