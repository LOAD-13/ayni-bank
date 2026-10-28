package pe.ayni.bank.core.interbancaria;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResolucionInterbancariaService {

    private static final UUID CUENTA_COMPENSACION =
        UUID.fromString("cce00000-0000-4000-8000-000000000001");

    private final JdbcTemplate db;

    public ResolucionInterbancariaService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional
    public void resolver(UUID id, EstadoInterbancario estado) {
        if (estado == null || estado == EstadoInterbancario.PENDIENTE) {
            return;
        }

        var filas = db.query(
            """
            SELECT origen_id, importe
            FROM transferencia_interbancaria
            WHERE id = ? AND estado = 'PENDIENTE'
            FOR UPDATE
            """,
            (rs, n) -> new Object[] {
                UUID.fromString(rs.getString("origen_id")),
                rs.getBigDecimal("importe")
            },
            id
        );

        if (filas.isEmpty()) {
            return; // Ya resuelta por otra solicitud.
        }

        if (estado == EstadoInterbancario.COMPLETADA) {
            db.update(
                """
                UPDATE transferencia_interbancaria
                SET estado = 'COMPLETADA', actualizado_en = now()
                WHERE id = ? AND estado = 'PENDIENTE'
                """,
                id
            );
            return;
        }

        if (estado != EstadoInterbancario.RECHAZADA) {
            throw new IllegalArgumentException("Transicion no permitida");
        }

        UUID origen = (UUID) filas.get(0)[0];
        BigDecimal importe = (BigDecimal) filas.get(0)[1];

        // Misma regla de bloqueo de cuenta que el cargo inicial.
        db.query(
            "SELECT id FROM cuenta WHERE id = ? FOR UPDATE",
            (rs, n) -> rs.getString("id"),
            origen
        );

        UUID movimientoReversion = UUID.randomUUID();
        registrarAsiento(
            CUENTA_COMPENSACION,
            movimientoReversion,
            "CARGO",
            importe,
            "Reversion compensacion interbancaria"
        );
        registrarAsiento(
            origen,
            movimientoReversion,
            "ABONO",
            importe,
            "Devolucion transferencia interbancaria"
        );

        db.update(
            """
            UPDATE transferencia_interbancaria
            SET estado = 'RECHAZADA',
                movimiento_reversion = ?,
                motivo = 'RECHAZO_CAMARA',
                actualizado_en = now()
            WHERE id = ? AND estado = 'PENDIENTE'
            """,
            movimientoReversion,
            id
        );
    }

    private void registrarAsiento(
        UUID cuentaId,
        UUID movimientoId,
        String tipo,
        BigDecimal importe,
        String concepto
    ) {
        db.update(
            """
            INSERT INTO asiento(
                id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto
            ) VALUES (?, ?, ?, ?, ?, 'PEN', ?)
            """,
            UUID.randomUUID(),
            cuentaId,
            movimientoId,
            tipo,
            importe,
            concepto
        );
    }
}
