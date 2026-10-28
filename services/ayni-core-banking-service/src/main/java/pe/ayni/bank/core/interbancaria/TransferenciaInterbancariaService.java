
package pe.ayni.bank.core.interbancaria;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.domain.model.Cci;

@Service
public class TransferenciaInterbancariaService {

    private static final UUID COMPENSACION =
        UUID.fromString(
            "cce00000-0000-4000-8000-000000000001"
        );

    private final JdbcTemplate db;
    private final CamaraPort camara;
    private final Duration espera;

    public record Solicitud(
        UUID usuarioId,
        UUID clave,
        String cci,
        BigDecimal importe,
        String concepto
    ) {}

    public record Resultado(
        UUID id,
        EstadoInterbancario estado
    ) {}

    public record Beneficiario(
        UUID id,
        String alias,
        String cci
    ) {}

    public TransferenciaInterbancariaService(
        JdbcTemplate db,
        CamaraPort camara,
        @Value("${ayni.camara.espera-maxima:PT5M}")
        Duration espera
    ) {
        this.db = db;
        this.camara = camara;
        this.espera = espera;
    }

    @Transactional
    public Resultado iniciar(Solicitud s) {

        if (s.usuarioId() == null ||
            s.clave() == null ||
            s.importe() == null) {
            throw new IllegalArgumentException(
                "Datos obligatorios"
            );
        }

        new Cci(s.cci());

        if (s.importe().scale() > 2 ||
            s.importe().compareTo(BigDecimal.ONE) < 0 ||
            s.importe().compareTo(
                new BigDecimal("5000")) > 0) {
            throw new IllegalArgumentException(
                "Importe fuera de rango"
            );
        }


var previos = db.query(
    """
    SELECT id, estado, cci_destino, importe, concepto
    FROM transferencia_interbancaria
    WHERE clave = ? AND usuario_id = ?
    """,
    (rs, n) -> new Object[] {
        UUID.fromString(rs.getString("id")),
        EstadoInterbancario.valueOf(
            rs.getString("estado")
        ),
        rs.getString("cci_destino"),
        rs.getBigDecimal("importe"),
        rs.getString("concepto")
    },
    s.clave(),
    s.usuarioId()
);

if (!previos.isEmpty()) {

    Object[] anterior = previos.get(0);

    String conceptoActual =
        s.concepto() == null || s.concepto().isBlank()
            ? "Transferencia interbancaria"
            : s.concepto().trim();

    boolean mismoCci =
        anterior[2].equals(s.cci());

    boolean mismoImporte =
        ((BigDecimal) anterior[3])
            .compareTo(s.importe()) == 0;

    boolean mismoConcepto =
        anterior[4].equals(conceptoActual);

    if (!mismoCci || !mismoImporte || !mismoConcepto) {
        throw new IllegalArgumentException(
            "Clave de idempotencia utilizada con otros datos"
        );
    }

    return new Resultado(
        (UUID) anterior[0],
        (EstadoInterbancario) anterior[1]
    );
}


        List<UUID> origen = db.query(
            """
            SELECT id
            FROM cuenta
            WHERE usuario_id = ?
              AND moneda = 'PEN'
              AND estado = 'ACTIVA'
            FOR UPDATE
            """,
            (rs, n) ->
                UUID.fromString(rs.getString("id")),
            s.usuarioId()
        );

        if (origen.size() != 1) {
            throw new IllegalArgumentException(
                "Cuenta PEN activa no encontrada"
            );
        }

        UUID cuenta = origen.get(0);

        BigDecimal saldo = db.queryForObject(
            """
            SELECT COALESCE(SUM(
                CASE
                    WHEN tipo = 'ABONO' THEN importe
                    ELSE -importe
                END
            ), 0)
            FROM asiento
            WHERE cuenta_id = ?
            """,
            BigDecimal.class,
            cuenta
        );

        if (saldo.compareTo(s.importe()) < 0) {
            throw new IllegalArgumentException(
                "Saldo insuficiente"
            );
        }

        String concepto =
            s.concepto() == null ||
            s.concepto().isBlank()
                ? "Transferencia interbancaria"
                : s.concepto().trim();

        if (concepto.length() > 140) {
            throw new IllegalArgumentException(
                "Concepto demasiado largo"
            );
        }

        UUID id = UUID.randomUUID();
        UUID movimiento = UUID.randomUUID();

        db.update(
            """
            INSERT INTO transferencia_interbancaria(
                id, clave, usuario_id, origen_id,
                cci_destino, importe, concepto,
                movimiento_cargo, vence_en
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                    now() + (? * interval '1 millisecond'))
            """,
            id,
            s.clave(),
            s.usuarioId(),
            cuenta,
            s.cci(),
            s.importe(),
            concepto,
            movimiento,
            espera.toMillis()
        );

        asiento(
            cuenta, movimiento,
            "CARGO", s.importe(),
            "Salida interbancaria"
        );

        asiento(
            COMPENSACION, movimiento,
            "ABONO", s.importe(),
            "Compensacion interbancaria"
        );

        return new Resultado(
            id, EstadoInterbancario.PENDIENTE
        );
    }

    private void asiento(
        UUID cuenta,
        UUID movimiento,
        String tipo,
        BigDecimal importe,
        String concepto
    ) {
        db.update(
            """
            INSERT INTO asiento(
                id, cuenta_id, movimiento_id,
                tipo, importe, moneda, concepto
            )
            VALUES (?, ?, ?, ?, ?, 'PEN', ?)
            """,
            UUID.randomUUID(),
            cuenta,
            movimiento,
            tipo,
            importe,
            concepto
        );
    }

    public Resultado consultar(UUID id, UUID usuario) {
        return db.query(
            """
            SELECT id, estado
            FROM transferencia_interbancaria
            WHERE id = ? AND usuario_id = ?
            """,
            (rs, n) -> new Resultado(
                UUID.fromString(rs.getString("id")),
                EstadoInterbancario.valueOf(
                    rs.getString("estado")
                )
            ),
            id, usuario
        ).stream().findFirst().orElseThrow(
            () -> new IllegalArgumentException(
                "Transferencia no encontrada"
            )
        );
    }

    public void despachar(UUID id) {

        var filas = db.query(
            """
            SELECT cci_destino, importe
            FROM transferencia_interbancaria
            WHERE id = ? AND estado = 'PENDIENTE'
            """,
            (rs, n) -> new String[] {
                rs.getString("cci_destino"),
                rs.getBigDecimal("importe").toPlainString()
            },
            id
        );

        if (filas.isEmpty()) {
            return;
        }

        try {
            EstadoInterbancario respuesta = camara.enviar(
                id,
                filas.get(0)[0],
                filas.get(0)[1]
            );

            resolver(id, respuesta);

        } catch (RuntimeException ex) {
            // Resultado desconocido.
            // No revertir hasta conocer el estado real.
        }
    }

    @Transactional
    public void resolver(
        UUID id,
        EstadoInterbancario estado
    ) {

        if (estado == EstadoInterbancario.PENDIENTE) {
            return;
        }

        var filas = db.query(
            """
            SELECT origen_id, importe
            FROM transferencia_interbancaria
            WHERE id = ?
              AND estado = 'PENDIENTE'
            FOR UPDATE
            """,
            (rs, n) -> new Object[] {
                UUID.fromString(
                    rs.getString("origen_id")
                ),
                rs.getBigDecimal("importe")
            },
            id
        );

        if (filas.isEmpty()) {
            return;
        }

        if (estado == EstadoInterbancario.RECHAZADA) {

            UUID origen = (UUID) filas.get(0)[0];
            BigDecimal importe =
                (BigDecimal) filas.get(0)[1];

            UUID reversion = UUID.randomUUID();

            db.query(
                """
                SELECT id FROM cuenta
                WHERE id = ? FOR UPDATE
                """,
                (rs, n) -> rs.getString("id"),
                origen
            );

            asiento(
                COMPENSACION,
                reversion,
                "CARGO",
                importe,
                "Reversion compensacion"
            );

            asiento(
                origen,
                reversion,
                "ABONO",
                importe,
                "Reversion interbancaria"
            );

            db.update(
                """
                UPDATE transferencia_interbancaria
                SET estado = 'RECHAZADA',
                    movimiento_reversion = ?,
                    motivo = 'RECHAZO_CAMARA',
                    actualizado_en = now()
                WHERE id = ?
                """,
                reversion, id
            );

        } else if (
            estado == EstadoInterbancario.COMPLETADA
        ) {

            db.update(
                """
                UPDATE transferencia_interbancaria
                SET estado = 'COMPLETADA',
                    actualizado_en = now()
                WHERE id = ?
                """,
                id
            );
        }
    }


    public List<Beneficiario> beneficiarios(
        UUID usuario
    ) {
        return db.query(
            """
            SELECT id, alias, cci
            FROM beneficiario_interbancario
            WHERE usuario_id = ?
            ORDER BY alias
            """,
            (rs, n) -> new Beneficiario(
                UUID.fromString(rs.getString("id")),
                rs.getString("alias"),
                rs.getString("cci")
            ),
            usuario
        );
    }

    public Beneficiario guardarBeneficiario(
        UUID usuario,
        String alias,
        String cci
    ) {
        new Cci(cci);

        if (alias == null || alias.isBlank()
            || alias.length() > 80) {
            throw new IllegalArgumentException(
                "Alias invalido"
            );
        }

        UUID id = UUID.randomUUID();

        return db.query(
            """
            INSERT INTO beneficiario_interbancario(
                id, usuario_id, alias, cci
            )
            VALUES (?, ?, ?, ?)
            ON CONFLICT(usuario_id, cci)
            DO UPDATE SET alias = EXCLUDED.alias
            RETURNING id, alias, cci
            """,
            (rs, n) -> new Beneficiario(
                UUID.fromString(rs.getString("id")),
                rs.getString("alias"),
                rs.getString("cci")
            ),
            id, usuario, alias.trim(), cci
        ).get(0);
    }

    public void borrarBeneficiario(
        UUID usuario,
        UUID id
    ) {
        db.update(
            """
            DELETE FROM beneficiario_interbancario
            WHERE usuario_id = ? AND id = ?
            """,
            usuario, id
        );
    }
}
