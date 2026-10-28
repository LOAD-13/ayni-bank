package pe.ayni.bank.core.infrastructure.interbancaria;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pregunta a la camara por las transferencias que siguen PENDIENTE (HU-11).
 *
 * <p>Si la camara ya decidio, se aplica su respuesta. Si sigue sin decidir, o ni siquiera
 * contesta, la transferencia espera mientras este dentro del plazo; vencido el plazo, se
 * revierte el cargo y el cliente recupera su dinero (criterio «si la camara no responde en el
 * plazo, el importe se reversa»).
 */
@Component
public class ConciliadorInterbancario {

    private static final Logger log = LoggerFactory.getLogger(ConciliadorInterbancario.class);

    private final JdbcTemplate db;
    private final CamaraPort camara;
    private final ResolucionInterbancariaService resolucion;
    private final Clock reloj;

    private record Pendiente(UUID id, Instant venceEn) {
    }

    public ConciliadorInterbancario(JdbcTemplate db, CamaraPort camara,
            ResolucionInterbancariaService resolucion, Clock reloj) {
        this.db = db;
        this.camara = camara;
        this.resolucion = resolucion;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${ayni.camara.conciliacion-ms:15000}")
    public void conciliar() {
        List<Pendiente> pendientes = db.query("""
                SELECT id, vence_en FROM core.transferencia_interbancaria
                WHERE estado = 'PENDIENTE' ORDER BY vence_en LIMIT 50
                """, (rs, n) -> new Pendiente(UUID.fromString(rs.getString("id")),
                        rs.getObject("vence_en", Timestamp.class).toInstant()));
        for (Pendiente p : pendientes) {
            conciliar(p);
        }
    }

    private void conciliar(Pendiente p) {
        boolean vencida = !reloj.instant().isBefore(p.venceEn());
        EstadoInterbancario estado;
        try {
            estado = camara.consultar(p.id());
        } catch (RuntimeException sinRespuesta) {
            log.warn("La camara no respondio por la transferencia {}", p.id());
            estado = EstadoInterbancario.PENDIENTE;
        }
        if (estado != EstadoInterbancario.PENDIENTE) {
            resolucion.resolver(p.id(), estado, ResolucionInterbancariaService.RECHAZO_DE_LA_CAMARA);
        } else if (vencida) {
            resolucion.resolver(p.id(), EstadoInterbancario.RECHAZADA,
                    ResolucionInterbancariaService.SIN_RESPUESTA_EN_PLAZO);
        }
    }
}
