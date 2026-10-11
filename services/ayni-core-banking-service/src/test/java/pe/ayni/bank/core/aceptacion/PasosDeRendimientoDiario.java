package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.Before;
import io.cucumber.java.ParameterType;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.aceptacion.ContextoDeAceptacion.RelojDePrueba;
import pe.ayni.bank.core.domain.model.CalculoDeInteres;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Devengo;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.Rendimiento;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.in.CerrarDiaUseCase;
import pe.ayni.bank.core.domain.port.in.ConsultarRendimientoUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;

/** Pasos de {@code rendimiento-diario.feature} · HU-06. */
public class PasosDeRendimientoDiario {

    private static final short AHORRO_PEN = 1;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private AbrirCuentaDeAhorroUseCase abrir;
    @Autowired
    private CerrarDiaUseCase cierre;
    @Autowired
    private ConsultarRendimientoUseCase rendimiento;
    @Autowired
    private LibroMayorPort libro;

    private Cuenta ana;

    @ParameterType("\\d{4}-\\d{2}-\\d{2}")
    public LocalDate fecha(String texto) {
        return LocalDate.parse(texto);
    }

    /** Cada escenario empieza con la base como la dejan las migraciones. */
    @Before
    public void limpiar() {
        jdbc.update("DELETE FROM core.devengo_interes");
        jdbc.update("DELETE FROM core.capitalizacion_interes");
        jdbc.update("DELETE FROM core.cierre_diario");
        jdbc.update("DELETE FROM core.evento_auditoria");
        jdbc.update("DELETE FROM core.outbox");
        jdbc.update("DELETE FROM core.operacion_idempotente");
        jdbc.update("DELETE FROM core.asiento");
        jdbc.update("DELETE FROM core.cuenta WHERE NOT tecnica");
        jdbc.update("DELETE FROM core.tasa_producto WHERE producto_id = ? AND vigencia_desde > DATE '2026-08-17'",
                AHORRO_PEN);
        jdbc.update("UPDATE core.tasa_producto SET vigencia_hasta = NULL WHERE producto_id = ?", AHORRO_PEN);
    }

    private void nuevaTasa(int porcentaje, LocalDate desde) {
        jdbc.update("UPDATE core.tasa_producto SET vigencia_hasta = ? WHERE producto_id = ? AND vigencia_hasta IS NULL",
                desde, AHORRO_PEN);
        BigDecimal tea = BigDecimal.valueOf(porcentaje).movePointLeft(2);
        jdbc.update("INSERT INTO core.tasa_producto (producto_id, tea, trea, vigencia_desde) VALUES (?, ?, ?, ?)",
                AHORRO_PEN, tea, tea, desde);
    }

    private void cerrar(LocalDate dia) {
        reloj.fijar(dia.plusDays(1).atTime(12, 0).toInstant(ZoneOffset.UTC));
        cierre.cerrar(dia);
    }

    private Map<String, Object> devengo(LocalDate dia) {
        return jdbc.queryForMap("SELECT importe, saldo_base, tea FROM core.devengo_interes WHERE cuenta_id = ? AND fecha = ?",
                ana.id(), dia);
    }

    private Map<String, Object> capitalizacion() {
        return jdbc.queryForMap("SELECT abonado, residuo, movimiento_id FROM core.capitalizacion_interes WHERE cuenta_id = ?",
                ana.id());
    }

    // ─── Dado ────────────────────────────────────────────────────────────────

    @Dado("que la Cuenta Ayni Soles tiene una TEA de {int} % desde el {fecha}")
    public void tasa(int porcentaje, LocalDate desde) {
        nuevaTasa(porcentaje, desde);
    }

    @Y("que Ana abrió su cuenta Ayni el {fecha}")
    public void abrirCuenta(LocalDate dia) {
        reloj.fijar(dia.atTime(12, 0).toInstant(ZoneOffset.UTC));
        ana = abrir.abrirPara(UUID.randomUUID(), UUID.randomUUID(), Moneda.PEN);
    }

    @Dado("que Ana tiene un saldo de S\\/ {word} desde el {fecha}")
    public void saldo(String importe, LocalDate dia) {
        libro.registrar(Movimiento.depositoSimulado(UUID.randomUUID(), libro.cuentaDeFondeo(Moneda.PEN), ana,
                Dinero.de(importe, Moneda.PEN), dia.atTime(15, 0).toInstant(ZoneOffset.UTC)));
    }

    @Y("que ya se ejecutó el cierre del {fecha}")
    public void yaCerrado(LocalDate dia) {
        cerrar(dia);
    }

    // ─── Cuando ──────────────────────────────────────────────────────────────

    @Cuando("se ejecuta el cierre del {fecha}")
    public void cierreDel(LocalDate dia) {
        cerrar(dia);
    }

    @Cuando("se ejecutan los cierres del {fecha} al {fecha}")
    public void cierres(LocalDate desde, LocalDate hasta) {
        desde.datesUntil(hasta.plusDays(1)).forEach(this::cerrar);
    }

    @Cuando("la TEA cambia a {int} % desde el {fecha}")
    public void cambioDeTasa(int porcentaje, LocalDate desde) {
        nuevaTasa(porcentaje, desde);
    }

    // ─── Entonces ────────────────────────────────────────────────────────────

    @Entonces("el devengo de Ana del {fecha} es de {word} sobre un saldo base de {word} al {word} %")
    public void devengoEs(LocalDate dia, String importe, String saldoBase, String porcentaje) {
        Map<String, Object> d = devengo(dia);
        assertThat((BigDecimal) d.get("importe")).isEqualByComparingTo(importe);
        assertThat((BigDecimal) d.get("saldo_base")).isEqualByComparingTo(saldoBase);
        assertThat(((BigDecimal) d.get("tea")).movePointRight(2)).isEqualByComparingTo(porcentaje);
    }

    @Y("ese importe es 1000 × \\(\\(1.05)^\\(1\\/360) − 1) con BigDecimal redondeado a 8 decimales con HALF_EVEN")
    public void formula() {
        BigDecimal calculado = CalculoDeInteres.interesDelDia(new BigDecimal("1000.00"), new BigDecimal("0.05"));
        assertThat(calculado.scale()).isEqualTo(8);
        assertThat(calculado).isEqualByComparingTo("0.13553742");
    }

    @Entonces("a Ana se le abonan S\\/ {word} de intereses con asientos que suman cero")
    public void abonan(String importe) {
        Map<String, Object> c = capitalizacion();
        assertThat((BigDecimal) c.get("abonado")).isEqualByComparingTo(importe);
        UUID movimiento = (UUID) c.get("movimiento_id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.asiento WHERE movimiento_id = ?", Integer.class,
                movimiento)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END) FROM core.asiento
                 WHERE movimiento_id = ?
                """, BigDecimal.class, movimiento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.devengo_interes WHERE capitalizacion_id IS NULL",
                Integer.class)).isZero();
    }

    @Y("el saldo de Ana es S\\/ {word}")
    public void saldoEs(String importe) {
        assertThat(libro.saldoDe(ana.id(), Moneda.PEN)).isEqualTo(Dinero.de(importe, Moneda.PEN));
    }

    @Y("la fracción de {word} pasa a noviembre")
    public void residuo(String fraccion) {
        assertThat((BigDecimal) capitalizacion().get("residuo")).isEqualByComparingTo(fraccion);
    }

    @Y("se publica el evento InteresCapitalizado para avisar a Ana")
    public void evento() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM core.outbox WHERE tipo_evento = 'InteresCapitalizado'
                   AND carga ->> 'usuarioId' = ? AND carga ->> 'importe' = '4.20' AND carga ->> 'periodo' = '2026-10'
                """, Integer.class, ana.usuarioId().toString())).isEqualTo(1);
    }

    @Y("la consulta de rendimiento del {fecha} muestra la TREA de {word} % y los devengos al {word} % y al {word} %")
    public void consulta(LocalDate dia, String trea, String uno, String otro) {
        reloj.fijar(dia.atTime(17, 0).toInstant(ZoneOffset.UTC));
        Rendimiento r = rendimiento.deTitular(ana.usuarioId()).orElseThrow();
        assertThat(r.tasa().trea().movePointRight(2)).isEqualByComparingTo(trea);
        assertThat(r.devengos()).extracting(Devengo::tea).extracting(t -> t.movePointRight(2).stripTrailingZeros())
                .containsExactly(new BigDecimal(otro).stripTrailingZeros(), new BigDecimal(uno).stripTrailingZeros());
        assertThat(r.devengadoDelMes()).isEqualByComparingTo("0.24448978");
    }

    @Entonces("Ana no tiene devengo del {fecha}")
    public void sinDevengo(LocalDate dia) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.devengo_interes WHERE cuenta_id = ? AND fecha = ?",
                Integer.class, ana.id(), dia)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.cierre_diario WHERE fecha = ?", Integer.class, dia))
                .isEqualTo(1);
    }
}
