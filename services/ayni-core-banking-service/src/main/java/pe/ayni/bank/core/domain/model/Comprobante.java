package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que el cliente recibe al terminar una operacion.
 *
 * <p>Las cuentas viajan enmascaradas: el comprobante se puede capturar, compartir o dejar
 * en pantalla, y el numero completo identifica al titular ante terceros.
 *
 * <p>{@code sentido} dice si para el titular que lo pide el dinero salio (CARGO) o entro (ABONO).
 */
public record Comprobante(UUID movimientoId, TipoDeMovimiento tipo, Dinero importe,
                          String cuentaOrigen, String cuentaDestino, String concepto,
                          Instant registradoEn, Dinero saldoDisponible,
                          TipoDeAsiento sentido) {
}
