package pe.ayni.bank.core.infrastructure.interbancaria;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.PublicadorDeEventosPort;

/**
 * Aplica la respuesta de la camara a una transferencia PENDIENTE, una sola vez: la fila se
 * bloquea y solo se cambia si sigue pendiente, asi que dos respuestas que llegan a la vez (el
 * envio y la conciliacion) no la resuelven dos veces.
 *
 * <p>COMPLETADA solo cambia el estado: el dinero ya salio al iniciar. RECHAZADA devuelve el
 * importe con un movimiento nuevo de compensacion; el cargo original no se toca, porque un
 * asiento no se corrige, se compensa. En los dos casos el titular recibe un aviso por correo
 * (evento {@code TransferenciaInterbancariaResuelta}, HU-13) y la pista de auditoria lo registra.
 */
@Service
public class ResolucionInterbancariaService {

    static final String RECHAZO_DE_LA_CAMARA = "RECHAZO_CAMARA";
    static final String SIN_RESPUESTA_EN_PLAZO = "SIN_RESPUESTA_EN_PLAZO";
    static final String EVENTO = "TransferenciaInterbancariaResuelta";

    private final JdbcTemplate db;
    private final PublicadorDeEventosPort eventos;
    private final PistaDeAuditoriaPort auditoria;
    private final Clock reloj;

    /** Lo que se publica; el CCI va recortado a sus cuatro ultimos digitos. */
    public record TransferenciaInterbancariaResuelta(UUID transferenciaId, UUID movimientoId, UUID usuarioId,
            String estado, String motivo, String cuenta, String cciFinal, String importe, String moneda,
            String saldo, String registradoEn) {
    }

    private record Pendiente(UUID usuarioId, UUID origen, BigDecimal importe, String cci, UUID cargo) {
    }

    public ResolucionInterbancariaService(JdbcTemplate db, PublicadorDeEventosPort eventos,
            PistaDeAuditoriaPort auditoria, Clock reloj) {
        this.db = db;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional
    public void resolver(UUID id, EstadoInterbancario estado, String motivo) {
        if (estado == null || estado == EstadoInterbancario.PENDIENTE) {
            return;
        }
        var filas = db.query("""
                SELECT usuario_id, origen_id, importe, cci_destino, movimiento_cargo
                FROM core.transferencia_interbancaria
                WHERE id = ? AND estado = 'PENDIENTE'
                FOR UPDATE
                """, (rs, n) -> new Pendiente(UUID.fromString(rs.getString("usuario_id")),
                        UUID.fromString(rs.getString("origen_id")), rs.getBigDecimal("importe"),
                        rs.getString("cci_destino"), UUID.fromString(rs.getString("movimiento_cargo"))), id);
        if (filas.isEmpty()) {
            return; // Ya la resolvio otra respuesta.
        }
        Pendiente p = filas.get(0);

        if (estado == EstadoInterbancario.COMPLETADA) {
            db.update("""
                    UPDATE core.transferencia_interbancaria SET estado = 'COMPLETADA', actualizado_en = now()
                    WHERE id = ? AND estado = 'PENDIENTE'
                    """, id);
            auditoria.registrar(TipoDeEventoDeOperacion.INTERBANCARIA_COMPLETADA, p.usuarioId(), p.cargo());
            publicar(id, p, p.cargo(), estado, null);
            return;
        }

        // Misma regla de bloqueo de la cuenta que el cargo inicial.
        db.query("SELECT id FROM core.cuenta WHERE id = ? FOR UPDATE", (rs, n) -> rs.getString("id"), p.origen());
        UUID reversion = UUID.randomUUID();
        asiento(TransferenciaInterbancariaService.COMPENSACION, reversion, "CARGO", p.importe(),
                "Reversion compensacion interbancaria");
        asiento(p.origen(), reversion, "ABONO", p.importe(), "Devolucion transferencia interbancaria");
        db.update("""
                UPDATE core.transferencia_interbancaria
                SET estado = 'RECHAZADA', movimiento_reversion = ?, motivo = ?, actualizado_en = now()
                WHERE id = ? AND estado = 'PENDIENTE'
                """, reversion, motivo, id);
        auditoria.registrar(TipoDeEventoDeOperacion.INTERBANCARIA_REVERTIDA, p.usuarioId(), reversion);
        publicar(id, p, reversion, EstadoInterbancario.RECHAZADA, motivo);
    }

    private void publicar(UUID id, Pendiente p, UUID movimiento, EstadoInterbancario estado, String motivo) {
        // Nada sensible en el evento: el numero de cuenta enmascarado, como en HU-13.
        String cuenta = new NumeroDeCuenta(db.queryForObject("SELECT numero FROM core.cuenta WHERE id = ?",
                String.class, p.origen())).enmascarado();
        BigDecimal saldo = db.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
                FROM core.asiento WHERE cuenta_id = ?
                """, BigDecimal.class, p.origen());
        eventos.registrar("TransferenciaInterbancaria", id, EVENTO, new TransferenciaInterbancariaResuelta(
                id, movimiento, p.usuarioId(), estado.name(), motivo, cuenta, p.cci().substring(16),
                p.importe().setScale(2, RoundingMode.HALF_EVEN).toPlainString(), "PEN", saldo.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                reloj.instant().toString()));
    }

    private void asiento(UUID cuenta, UUID movimiento, String tipo, BigDecimal importe, String concepto) {
        db.update("""
                INSERT INTO core.asiento(id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto)
                VALUES (?, ?, ?, ?, ?, 'PEN', ?)
                """, UUID.randomUUID(), cuenta, movimiento, tipo, importe, concepto);
    }
}
