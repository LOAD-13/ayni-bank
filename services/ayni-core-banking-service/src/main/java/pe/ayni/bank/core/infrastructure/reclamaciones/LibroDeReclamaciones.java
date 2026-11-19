package pe.ayni.bank.core.infrastructure.reclamaciones;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El libro de reclamaciones virtual: guarda cada hoja con su numero correlativo y el plazo
 * maximo de respuesta, 15 dias habiles desde que se registra.
 *
 * <p>Los datos del consumidor son personales: este servicio no los escribe en ningun log.
 */
@Service
public class LibroDeReclamaciones {

    static final int DIAS_HABILES_PARA_RESPONDER = 15;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final JdbcTemplate db;
    private final Clock reloj;

    public LibroDeReclamaciones(JdbcTemplate db, Clock reloj) {
        this.db = db;
        this.reloj = reloj;
    }

    public enum Tipo { RECLAMO, QUEJA }

    public enum Bien { PRODUCTO, SERVICIO }

    public record Hoja(Tipo tipo, String nombre, String tipoDocumento, String numeroDocumento,
                       String correo, String telefono, Bien bien, String descripcionDelBien,
                       BigDecimal montoReclamado, String detalle, String pedido) {
    }

    public record Constancia(String codigo, Tipo tipo, Instant registradaEn, LocalDate responderAntesDe) {
    }

    @Transactional
    public Constancia registrar(Hoja hoja) {
        Instant ahora = reloj.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, LIMA);
        LocalDate plazo = sumarDiasHabiles(hoy, DIAS_HABILES_PARA_RESPONDER);
        Long numero = db.queryForObject("""
                INSERT INTO core.reclamacion (id, tipo, nombre, tipo_documento, numero_documento, correo, telefono,
                                         bien, descripcion_del_bien, monto_reclamado, detalle, pedido,
                                         registrada_en, responder_antes_de)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING numero
                """, Long.class,
                UUID.randomUUID(), hoja.tipo().name(), hoja.nombre().strip(), hoja.tipoDocumento(),
                hoja.numeroDocumento(), hoja.correo().strip(), vacioANulo(hoja.telefono()), hoja.bien().name(),
                hoja.descripcionDelBien().strip(), hoja.montoReclamado(), hoja.detalle().strip(),
                hoja.pedido().strip(), Timestamp.from(ahora), plazo);
        return new Constancia(codigo(hoy.getYear(), numero), hoja.tipo(), ahora, plazo);
    }

    /** El año de registro y el correlativo, que nunca se reutiliza: LR-2026-000042. */
    static String codigo(int anio, long numero) {
        return "LR-%d-%06d".formatted(anio, numero);
    }

    // shortcut: solo salta sabados y domingos; los feriados nacionales no se descuentan
    // (el plazo puede vencer uno o dos dias antes). Si se publica de verdad, usar un calendario.
    static LocalDate sumarDiasHabiles(LocalDate desde, int dias) {
        LocalDate fecha = desde;
        int contados = 0;
        while (contados < dias) {
            fecha = fecha.plusDays(1);
            if (fecha.getDayOfWeek() != DayOfWeek.SATURDAY && fecha.getDayOfWeek() != DayOfWeek.SUNDAY) {
                contados++;
            }
        }
        return fecha;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
