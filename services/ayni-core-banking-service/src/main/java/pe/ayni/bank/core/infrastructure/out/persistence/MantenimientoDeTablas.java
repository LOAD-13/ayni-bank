package pe.ayni.bank.core.infrastructure.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantenimiento nocturno de las tablas que crecen sin parar (AYNI-155, ADR-0036).
 *
 * <ul>
 *   <li>Crea las particiones mensuales de {@code asiento} con meses de antelacion.</li>
 *   <li>Purga el outbox ya publicado: una vez en RabbitMQ, la fila no sirve para nada.</li>
 *   <li>Purga las claves de idempotencia viejas: un reintento llega en segundos o minutos,
 *       no al mes.</li>
 * </ul>
 *
 * <p>Nunca toca un asiento: la pista de auditoria es inalterable (HU-15). Borra por lotes
 * para no retener bloqueos ni llenar el WAL de golpe. Si hay varias instancias, ejecutarlo
 * dos veces no hace dano: todo lo que hace es idempotente.
 */
@Component
public class MantenimientoDeTablas {

    private static final Logger log = LoggerFactory.getLogger(MantenimientoDeTablas.class);

    private final JdbcTemplate jdbc;
    private final int mesesDeAntelacion;
    private final int diasDeOutbox;
    private final int diasDeIdempotencia;
    private final int lote;

    public MantenimientoDeTablas(JdbcTemplate jdbc,
            @Value("${ayni.mantenimiento.meses-de-antelacion}") int mesesDeAntelacion,
            @Value("${ayni.mantenimiento.dias-de-outbox}") int diasDeOutbox,
            @Value("${ayni.mantenimiento.dias-de-idempotencia}") int diasDeIdempotencia,
            @Value("${ayni.mantenimiento.lote}") int lote) {
        this.jdbc = jdbc;
        this.mesesDeAntelacion = mesesDeAntelacion;
        this.diasDeOutbox = diasDeOutbox;
        this.diasDeIdempotencia = diasDeIdempotencia;
        this.lote = lote;
    }

    @Scheduled(cron = "${ayni.mantenimiento.cron}", zone = "America/Lima")
    public void ejecutar() {
        Integer creadas = jdbc.queryForObject("SELECT core.crear_particiones_de_asiento(?)", Integer.class,
                mesesDeAntelacion);
        if (creadas != null && creadas > 0) {
            log.info("Creadas {} particiones nuevas de asiento", creadas);
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM core.asiento_default)", Boolean.class))) {
            log.warn("Hay asientos en asiento_default: falta la particion de su mes. Revisar el mantenimiento.");
        }

        int outbox = purgar("""
                DELETE FROM core.outbox WHERE id IN (
                    SELECT id FROM core.outbox
                    WHERE publicado_en < now() - make_interval(days => ?) LIMIT ?)""", diasDeOutbox);
        int claves = purgar("""
                DELETE FROM core.operacion_idempotente WHERE clave IN (
                    SELECT clave FROM core.operacion_idempotente
                    WHERE registrado_en < now() - make_interval(days => ?) LIMIT ?)""", diasDeIdempotencia);
        log.info("Mantenimiento terminado: {} eventos del outbox y {} claves de idempotencia purgados", outbox, claves);
    }

    private int purgar(String sentencia, int dias) {
        int total = 0;
        int borradas;
        do {
            borradas = jdbc.update(sentencia, dias, lote);
            total += borradas;
        } while (borradas == lote);
        return total;
    }
}
