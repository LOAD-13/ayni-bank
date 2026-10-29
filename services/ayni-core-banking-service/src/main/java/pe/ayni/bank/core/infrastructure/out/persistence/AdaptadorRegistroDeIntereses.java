package pe.ayni.bank.core.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.ayni.bank.core.domain.model.CalculoDeInteres;
import pe.ayni.bank.core.domain.model.Capitalizacion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.TasaVigente;
import pe.ayni.bank.core.domain.port.out.RegistroDeInteresesPort;

/**
 * Persistencia de HU-06 con SQL explicito.
 *
 * <p>Son agregaciones y marcas por lotes (sumar devengos, asignarlos a una capitalizacion)
 * que en JPA costarian una entidad por tabla y varias idas a la base. Todas las tablas van
 * cualificadas con el schema: las consultas nativas no usan el schema por defecto de Hibernate.
 */
@Component
public class AdaptadorRegistroDeIntereses implements RegistroDeInteresesPort {

    private static final int DECIMALES = CalculoDeInteres.DECIMALES_DEL_DEVENGO;

    private final JdbcTemplate jdbc;
    private final CuentaJpaRepository cuentas;

    AdaptadorRegistroDeIntereses(JdbcTemplate jdbc, CuentaJpaRepository cuentas) {
        this.jdbc = jdbc;
        this.cuentas = cuentas;
    }

    @Override
    public Optional<LocalDate> ultimoCierre() {
        return Optional.ofNullable(jdbc.queryForObject("SELECT max(fecha) FROM core.cierre_diario", LocalDate.class));
    }

    @Override
    public boolean estaCerrado(LocalDate fecha) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM core.cierre_diario WHERE fecha = ?)", Boolean.class, fecha));
    }

    @Override
    public List<Cuenta> cuentasQueDevengan(Instant corte) {
        List<UUID> ids = jdbc.queryForList("""
                SELECT c.id FROM core.cuenta c JOIN core.producto p ON p.id = c.producto_id
                 WHERE p.tipo = 'AHORRO' AND c.estado = 'ACTIVA' AND NOT c.tecnica AND c.abierta_en < ?
                 ORDER BY c.id
                """, UUID.class, Timestamp.from(corte));
        return cuentas.findAllById(ids).stream().map(AdaptadorLibroMayor::aDominio).toList();
    }

    @Override
    public Dinero saldoAlCorte(UUID cuentaId, Moneda moneda, Instant corte) {
        BigDecimal saldo = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
                  FROM core.asiento WHERE cuenta_id = ? AND registrado_en < ?
                """, BigDecimal.class, cuentaId, Timestamp.from(corte));
        return new Dinero(saldo, moneda);
    }

    @Override
    public Optional<TasaVigente> tasaVigente(short productoId, LocalDate fecha) {
        return jdbc.query("""
                SELECT id, tea, trea, vigencia_desde FROM core.tasa_producto
                 WHERE producto_id = ? AND vigencia_desde <= ? AND (vigencia_hasta IS NULL OR vigencia_hasta > ?)
                 ORDER BY vigencia_desde DESC LIMIT 1
                """, (rs, i) -> new TasaVigente(rs.getLong("id"), rs.getBigDecimal("tea"), rs.getBigDecimal("trea"),
                        rs.getObject("vigencia_desde", LocalDate.class)),
                productoId, fecha, fecha).stream().findFirst();
    }

    @Override
    public void registrar(Devengo d) {
        jdbc.update("""
                INSERT INTO core.devengo_interes (id, cuenta_id, fecha, saldo_base, tasa_producto_id, tea, importe,
                                                  registrado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, d.id(), d.cuentaId(), d.fecha(), d.saldoBase().importe(), d.tasaId(), d.tea(), d.importe(),
                Timestamp.from(d.registradoEn()));
    }

    @Override
    public void registrarCierre(LocalDate fecha, int cuentasConDevengo, BigDecimal devengado, Instant ahora) {
        jdbc.update("INSERT INTO core.cierre_diario (fecha, cuentas, devengado, procesado_en) VALUES (?, ?, ?, ?)",
                fecha, cuentasConDevengo, devengado, Timestamp.from(ahora));
    }

    @Override
    public List<UUID> cuentasConDevengoPendiente(LocalDate hasta) {
        return jdbc.queryForList("""
                SELECT DISTINCT cuenta_id FROM core.devengo_interes
                 WHERE capitalizacion_id IS NULL AND fecha <= ? ORDER BY cuenta_id
                """, UUID.class, hasta);
    }

    @Override
    public BigDecimal devengadoSinCapitalizar(UUID cuentaId, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(importe), 0) FROM core.devengo_interes
                 WHERE cuenta_id = ? AND capitalizacion_id IS NULL AND fecha <= ?
                """, BigDecimal.class, cuentaId, hasta).setScale(DECIMALES, RoundingMode.UNNECESSARY);
    }

    @Override
    public BigDecimal ultimoResiduo(UUID cuentaId) {
        return jdbc.query("""
                SELECT residuo FROM core.capitalizacion_interes WHERE cuenta_id = ? ORDER BY periodo DESC LIMIT 1
                """, (rs, i) -> rs.getBigDecimal(1), cuentaId).stream().findFirst()
                .orElse(BigDecimal.ZERO).setScale(DECIMALES, RoundingMode.UNNECESSARY);
    }

    @Override
    public void registrar(Capitalizacion c, LocalDate hasta, Instant ahora) {
        jdbc.update("""
                INSERT INTO core.capitalizacion_interes (id, cuenta_id, periodo, devengado, residuo_anterior, abonado,
                                                         residuo, movimiento_id, registrado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, c.id(), c.cuentaId(), c.periodo().atDay(1), c.devengado(), c.residuoAnterior(),
                c.abonado().importe(), c.residuo(), c.movimientoId(), Timestamp.from(ahora));
        jdbc.update("""
                UPDATE core.devengo_interes SET capitalizacion_id = ?
                 WHERE cuenta_id = ? AND capitalizacion_id IS NULL AND fecha <= ?
                """, c.id(), c.cuentaId(), hasta);
    }

    @Override
    public List<Devengo> ultimosDevengos(UUID cuentaId, int limite) {
        return jdbc.query("""
                SELECT d.id, d.cuenta_id, d.fecha, d.saldo_base, d.tasa_producto_id, d.tea, d.importe,
                       d.registrado_en, c.moneda
                  FROM core.devengo_interes d JOIN core.cuenta c ON c.id = d.cuenta_id
                 WHERE d.cuenta_id = ? ORDER BY d.fecha DESC LIMIT ?
                """, (rs, i) -> new Devengo(rs.getObject("id", UUID.class), rs.getObject("cuenta_id", UUID.class),
                        rs.getObject("fecha", LocalDate.class),
                        new Dinero(rs.getBigDecimal("saldo_base"), Moneda.valueOf(rs.getString("moneda"))),
                        rs.getLong("tasa_producto_id"), rs.getBigDecimal("tea"), rs.getBigDecimal("importe"),
                        rs.getTimestamp("registrado_en").toInstant()),
                cuentaId, limite);
    }

    @Override
    public BigDecimal devengadoEntre(UUID cuentaId, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(importe), 0) FROM core.devengo_interes
                 WHERE cuenta_id = ? AND fecha BETWEEN ? AND ?
                """, BigDecimal.class, cuentaId, desde, hasta).setScale(DECIMALES, RoundingMode.UNNECESSARY);
    }

    @Override
    public BigDecimal abonadoTotal(UUID cuentaId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(abonado), 0) FROM core.capitalizacion_interes WHERE cuenta_id = ?",
                BigDecimal.class, cuentaId);
    }
}
