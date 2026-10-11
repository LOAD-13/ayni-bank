package pe.ayni.bank.notification.infrastructure.in.messaging;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import pe.ayni.bank.notification.domain.model.AvisoDeMovimiento;
import pe.ayni.bank.notification.domain.model.TipoDeAviso;

/**
 * Convierte los eventos de core-banking en avisos para cada titular afectado.
 *
 * <p>Los importes llegan como texto con dos decimales y asi se tratan: se les da formato
 * sin pasar nunca por {@code double}.
 */
public final class TraductorDeMovimientos {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.of("es", "PE"));

    private TraductorDeMovimientos() {
    }

    /** Un aviso para quien envia y otro para quien recibe. */
    public static List<AvisoDeMovimiento> deTransferencia(UUID eventoId, TransferenciaRealizada e) {
        List<AvisoDeMovimiento> avisos = new ArrayList<>(2);
        if (e.usuarioOrigenId() != null) {
            avisos.add(new AvisoDeMovimiento(eventoId, e.usuarioOrigenId(), TipoDeAviso.TRANSFERENCIA_ENVIADA,
                    datos(e.movimientoId(), e.importe(), e.moneda(), e.cuentaOrigen(), e.saldoOrigen(),
                            e.registradoEn(), e.cuentaDestino())));
        }
        if (e.usuarioDestinoId() != null) {
            avisos.add(new AvisoDeMovimiento(eventoId, e.usuarioDestinoId(), TipoDeAviso.TRANSFERENCIA_RECIBIDA,
                    datos(e.movimientoId(), e.importe(), e.moneda(), e.cuentaDestino(), e.saldoDestino(),
                            e.registradoEn(), e.cuentaOrigen())));
        }
        return avisos;
    }

    public static List<AvisoDeMovimiento> deDeposito(UUID eventoId, DepositoSimuladoRegistrado e) {
        if (e.usuarioId() == null) {
            return List.of();
        }
        return List.of(new AvisoDeMovimiento(eventoId, e.usuarioId(), TipoDeAviso.DEPOSITO_RECIBIDO,
                datos(e.movimientoId(), e.importe(), e.moneda(), e.cuenta(), e.saldo(), e.registradoEn(), null)));
    }

    /**
     * El identificador del evento es el {@code messageId} que pone el outbox de core-banking.
     * Si faltara, se usa el del movimiento, que tambien es unico por evento.
     */
    public static UUID eventoId(String messageId, UUID movimientoId) {
        if (messageId != null) {
            try {
                return UUID.fromString(messageId);
            } catch (IllegalArgumentException noEsUnUuid) {
                // Se cae al identificador del movimiento.
            }
        }
        return movimientoId;
    }

    @SuppressWarnings("java:S107") // Son los campos de la plantilla, uno por marcador.
    private static Map<String, String> datos(UUID movimientoId, String importe, String moneda, String cuenta,
                                             String saldo, String registradoEn, String contraparte) {
        Map<String, String> datos = new HashMap<>();
        datos.put("importe", importe(importe));
        datos.put("moneda", moneda);
        datos.put("cuenta", cuenta);
        datos.put("saldo", importe(saldo));
        datos.put("fecha", FECHA.format(Instant.parse(registradoEn).atZone(LIMA)));
        datos.put("operacion", movimientoId.toString().substring(0, 8).toUpperCase(Locale.ROOT));
        if (contraparte != null) {
            datos.put("contraparte", contraparte);
        }
        return datos;
    }

    /** 1234.5 → "1,234.50". */
    static String importe(String valor) {
        var formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return formato.format(new BigDecimal(valor));
    }

    /** Lo que publica core-banking (HU-13). */
    public record TransferenciaRealizada(UUID movimientoId, UUID cuentaOrigenId, UUID cuentaDestinoId,
                                         String importe, String moneda, UUID usuarioOrigenId,
                                         UUID usuarioDestinoId, String cuentaOrigen, String cuentaDestino,
                                         String saldoOrigen, String saldoDestino, String registradoEn) {
    }

    public record DepositoSimuladoRegistrado(UUID movimientoId, UUID cuentaId, String importe, String moneda,
                                             UUID usuarioId, String cuenta, String saldo, String registradoEn) {
    }
}
