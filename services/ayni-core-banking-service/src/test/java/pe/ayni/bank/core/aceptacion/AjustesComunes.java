package pe.ayni.bank.core.aceptacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.Before;
import io.cucumber.java.ParameterType;

/**
 * Lo que comparten todas las features de core-banking.
 *
 * <p>En Cucumber los hooks y los tipos de parametro son globales a todo el glue: un
 * {@code @Before} declarado en los pasos de una feature corre tambien antes de los
 * escenarios de las demas, y un {@code {fecha}} declarado dos veces impide arrancar. Por
 * eso viven aqui y no en la clase de pasos de una historia concreta.
 */
public class AjustesComunes {

    private static final short AHORRO_PEN = 1;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CamaraDePrueba camara;

    @ParameterType("\\d{4}-\\d{2}-\\d{2}")
    public LocalDate fecha(String texto) {
        return LocalDate.parse(texto);
    }

    /**
     * Un importe como 120.50. No sirve {@code {bigdecimal}}: Cucumber lo lee con la configuracion
     * regional de la feature, y en espanol el punto separa miles: «500.00» seria 50 000.
     */
    @ParameterType("\\d+\\.\\d{2}")
    public BigDecimal importe(String texto) {
        return new BigDecimal(texto);
    }

    /** Cada escenario empieza con la base como la dejan las migraciones. */
    @Before(order = 0)
    public void limpiar() {
        camara.reiniciar();
        jdbc.update("DELETE FROM core.devengo_interes");
        jdbc.update("DELETE FROM core.capitalizacion_interes");
        jdbc.update("DELETE FROM core.cierre_diario");
        jdbc.update("DELETE FROM core.evento_auditoria");
        jdbc.update("DELETE FROM core.outbox");
        jdbc.update("DELETE FROM core.operacion_idempotente");
        jdbc.update("DELETE FROM core.control_tarjeta");
        jdbc.update("DELETE FROM core.tarjeta");
        jdbc.update("DELETE FROM core.transferencia_interbancaria");
        jdbc.update("DELETE FROM core.beneficiario_interbancario");
        jdbc.update("DELETE FROM core.asiento");
        jdbc.update("DELETE FROM core.cuenta WHERE NOT tecnica");
        jdbc.update("DELETE FROM core.tasa_producto WHERE producto_id = ? AND vigencia_desde > DATE '2026-08-17'",
                AHORRO_PEN);
        jdbc.update("UPDATE core.tasa_producto SET vigencia_hasta = NULL WHERE producto_id = ?", AHORRO_PEN);
    }
}
