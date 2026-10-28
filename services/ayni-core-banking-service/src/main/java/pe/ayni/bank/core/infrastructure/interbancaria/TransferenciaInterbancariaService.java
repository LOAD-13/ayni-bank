package pe.ayni.bank.core.infrastructure.interbancaria;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.core.application.interbancaria.ValidadorCci;
import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.Cci;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.OperacionAConfirmar;
import pe.ayni.bank.core.domain.model.TipoDeEventoDeOperacion;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.VerificadorDeConfirmacionPort;
import pe.ayni.bank.core.infrastructure.interbancaria.RechazoInterbancarioException.Motivo;

/**
 * HU-11: transferencia a otro banco por CCI.
 *
 * <p>El cargo se hace al iniciar, contra la cuenta tecnica de compensacion, y la transferencia
 * queda PENDIENTE hasta que la camara responde: si la acepta pasa a COMPLETADA, y si la rechaza
 * o no responde en el plazo, {@link ResolucionInterbancariaService} devuelve el importe. Como
 * cualquier movimiento de dinero, exige la confirmacion con segundo factor (ADR-0031), es
 * idempotente por su clave y deja rastro en la pista de auditoria.
 */
@Service
public class TransferenciaInterbancariaService {

    static final UUID COMPENSACION = UUID.fromString("cce00000-0000-4000-8000-000000000001");
    private static final String CONCEPTO_POR_DEFECTO = "Transferencia interbancaria";
    private static final BigDecimal MINIMO = BigDecimal.ONE;
    private static final BigDecimal MAXIMO = new BigDecimal("5000");

    private final JdbcTemplate db;
    private final CamaraPort camara;
    private final ResolucionInterbancariaService resolucion;
    private final VerificadorDeConfirmacionPort confirmaciones;
    private final PistaDeAuditoriaPort auditoria;
    private final Duration espera;

    public record Solicitud(UUID usuarioId, UUID clave, String cci, BigDecimal importe, String concepto) {
    }

    public record Resultado(UUID id, EstadoInterbancario estado) {
    }

    public record Beneficiario(UUID id, String alias, String cci) {
    }

    private record Previa(UUID id, EstadoInterbancario estado, String cci, BigDecimal importe, String concepto) {
    }

    public TransferenciaInterbancariaService(JdbcTemplate db, CamaraPort camara,
            ResolucionInterbancariaService resolucion, VerificadorDeConfirmacionPort confirmaciones,
            PistaDeAuditoriaPort auditoria, @Value("${ayni.camara.espera-maxima:PT5M}") Duration espera) {
        this.db = db;
        this.camara = camara;
        this.resolucion = resolucion;
        this.confirmaciones = confirmaciones;
        this.auditoria = auditoria;
        this.espera = espera;
    }

    @Transactional
    public Resultado iniciar(Solicitud s, String confirmacion) {
        validarCci(s.cci());
        validarImporte(s.importe());
        String concepto = concepto(s.concepto());

        Optional<Previa> previa = previa(s.clave(), s.usuarioId());
        if (previa.isPresent()) {
            return repetida(previa.get(), s, concepto);
        }

        // Una transferencia ya hecha no necesita confirmarse otra vez; una nueva si, y antes de
        // tocar ningun saldo. La confirmacion firma el CCI, el importe y la clave.
        confirmaciones.verificar(confirmacion, s.usuarioId(),
                new OperacionAConfirmar(s.cci(), new Dinero(s.importe(), Moneda.PEN), s.clave()));

        UUID cuenta = cuentaBloqueada(s.usuarioId());
        if (saldo(cuenta).compareTo(s.importe()) < 0) {
            throw new RechazoInterbancarioException(Motivo.SALDO_INSUFICIENTE);
        }

        UUID id = UUID.randomUUID();
        UUID movimiento = UUID.randomUUID();
        db.update("""
                INSERT INTO core.transferencia_interbancaria(
                    id, clave, usuario_id, origen_id, cci_destino, importe, concepto,
                    movimiento_cargo, vence_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now() + (? * interval '1 millisecond'))
                """, id, s.clave(), s.usuarioId(), cuenta, s.cci(), s.importe(), concepto, movimiento,
                espera.toMillis());
        asiento(cuenta, movimiento, "CARGO", s.importe(), "Salida interbancaria");
        asiento(COMPENSACION, movimiento, "ABONO", s.importe(), "Compensacion interbancaria");
        auditoria.registrar(TipoDeEventoDeOperacion.INTERBANCARIA_ENVIADA, s.usuarioId(), movimiento);

        return new Resultado(id, EstadoInterbancario.PENDIENTE);
    }

    private static void validarCci(String cci) {
        if (!ValidadorCci.esValido(cci)) {
            throw new RechazoInterbancarioException(Motivo.CCI_INVALIDO);
        }
        if (cci.startsWith(Cci.CODIGO_DE_BANCO)) {
            throw new RechazoInterbancarioException(Motivo.CCI_PROPIO);
        }
    }

    private static void validarImporte(BigDecimal importe) {
        if (importe == null || importe.scale() > 2 || importe.compareTo(MINIMO) < 0
                || importe.compareTo(MAXIMO) > 0) {
            throw new RechazoInterbancarioException(Motivo.IMPORTE_FUERA_DE_RANGO);
        }
    }

    private static String concepto(String concepto) {
        String limpio = concepto == null || concepto.isBlank() ? CONCEPTO_POR_DEFECTO : concepto.trim();
        if (limpio.length() > 140) {
            throw new RechazoInterbancarioException(Motivo.CONCEPTO_DEMASIADO_LARGO);
        }
        return limpio;
    }

    private Optional<Previa> previa(UUID clave, UUID usuarioId) {
        return db.query("""
                SELECT id, estado, cci_destino, importe, concepto
                FROM core.transferencia_interbancaria
                WHERE clave = ? AND usuario_id = ?
                """, (rs, n) -> new Previa(UUID.fromString(rs.getString("id")),
                        EstadoInterbancario.valueOf(rs.getString("estado")), rs.getString("cci_destino"),
                        rs.getBigDecimal("importe"), rs.getString("concepto")),
                clave, usuarioId).stream().findFirst();
    }

    /** La misma clave con los mismos datos es un reintento; con otros, un error del cliente. */
    private static Resultado repetida(Previa previa, Solicitud s, String concepto) {
        boolean mismaOperacion = previa.cci().equals(s.cci())
                && previa.importe().compareTo(s.importe()) == 0
                && previa.concepto().equals(concepto);
        if (!mismaOperacion) {
            throw new ClaveDeIdempotenciaReutilizadaException();
        }
        return new Resultado(previa.id(), previa.estado());
    }

    private UUID cuentaBloqueada(UUID usuarioId) {
        List<UUID> cuentas = db.query("""
                SELECT id FROM core.cuenta
                WHERE usuario_id = ? AND moneda = 'PEN' AND estado = 'ACTIVA'
                FOR UPDATE
                """, (rs, n) -> UUID.fromString(rs.getString("id")), usuarioId);
        if (cuentas.size() != 1) {
            throw new RechazoInterbancarioException(Motivo.SIN_CUENTA);
        }
        return cuentas.get(0);
    }

    private BigDecimal saldo(UUID cuenta) {
        return db.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
                FROM core.asiento WHERE cuenta_id = ?
                """, BigDecimal.class, cuenta);
    }

    private void asiento(UUID cuenta, UUID movimiento, String tipo, BigDecimal importe, String concepto) {
        db.update("""
                INSERT INTO core.asiento(id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto)
                VALUES (?, ?, ?, ?, ?, 'PEN', ?)
                """, UUID.randomUUID(), cuenta, movimiento, tipo, importe, concepto);
    }

    public Resultado consultar(UUID id, UUID usuario) {
        return db.query("""
                SELECT id, estado FROM core.transferencia_interbancaria WHERE id = ? AND usuario_id = ?
                """, (rs, n) -> new Resultado(UUID.fromString(rs.getString("id")),
                        EstadoInterbancario.valueOf(rs.getString("estado"))), id, usuario)
                .stream().findFirst()
                .orElseThrow(() -> new RechazoInterbancarioException(Motivo.NO_ENCONTRADA));
    }

    /**
     * Envia la instruccion a la camara y aplica su respuesta. Si la camara no contesta, la
     * transferencia sigue PENDIENTE: no se revierte un resultado que no se conoce, y la
     * conciliacion vuelve a preguntar hasta que responda o venza el plazo.
     */
    public void despachar(UUID id) {
        var filas = db.query("""
                SELECT cci_destino, importe FROM core.transferencia_interbancaria
                WHERE id = ? AND estado = 'PENDIENTE'
                """, (rs, n) -> new String[] {rs.getString("cci_destino"),
                        rs.getBigDecimal("importe").toPlainString()}, id);
        if (filas.isEmpty()) {
            return;
        }
        try {
            resolucion.resolver(id, camara.enviar(id, filas.get(0)[0], filas.get(0)[1]),
                    ResolucionInterbancariaService.RECHAZO_DE_LA_CAMARA);
        } catch (RuntimeException sinRespuesta) {
            // Resultado desconocido: lo resuelve la conciliacion.
        }
    }

    public List<Beneficiario> beneficiarios(UUID usuario) {
        return db.query("""
                SELECT id, alias, cci FROM core.beneficiario_interbancario
                WHERE usuario_id = ? ORDER BY alias
                """, (rs, n) -> new Beneficiario(UUID.fromString(rs.getString("id")), rs.getString("alias"),
                        rs.getString("cci")), usuario);
    }

    public Beneficiario guardarBeneficiario(UUID usuario, String alias, String cci) {
        validarCci(cci);
        if (alias == null || alias.isBlank() || alias.trim().length() > 80) {
            throw new RechazoInterbancarioException(Motivo.ALIAS_INVALIDO);
        }
        return db.query("""
                INSERT INTO core.beneficiario_interbancario(id, usuario_id, alias, cci)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(usuario_id, cci) DO UPDATE SET alias = EXCLUDED.alias
                RETURNING id, alias, cci
                """, (rs, n) -> new Beneficiario(UUID.fromString(rs.getString("id")), rs.getString("alias"),
                        rs.getString("cci")), UUID.randomUUID(), usuario, alias.trim(), cci).get(0);
    }

    public void borrarBeneficiario(UUID usuario, UUID id) {
        db.update("DELETE FROM core.beneficiario_interbancario WHERE usuario_id = ? AND id = ?", usuario, id);
    }
}
