package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un apunte sobre una cuenta. Es la unidad indivisible del sistema contable.
 *
 * <p>Los asientos no se editan ni se borran nunca. Corregir un error no es cambiar el
 * asiento equivocado sino anadir otro que lo compense: asi la historia queda completa y se
 * puede reconstruir cualquier saldo pasado. Por eso no hay ni un solo metodo que mute.
 *
 * <p>{@code movimientoId} agrupa los asientos de una misma operacion. En una transferencia
 * son dos —el cargo en origen y el abono en destino— y la partida doble exige que sumen
 * cero. Sin ese identificador comun no habria forma de comprobarlo.
 */
public record Asiento(UUID id, UUID cuentaId, UUID movimientoId, TipoDeAsiento tipo,
                      Dinero importe, String concepto, Instant registradoEn) {

    private static final int MAXIMO_CONCEPTO = 140;

    public Asiento {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cuentaId);
        Objects.requireNonNull(movimientoId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(importe);
        Objects.requireNonNull(registradoEn);

        // El signo lo lleva el tipo, no el importe. Un CARGO de importe negativo seria un
        // abono disfrazado y bastaria uno para que ninguna suma volviera a cuadrar.
        if (!importe.esPositivo()) {
            throw new IllegalArgumentException("El importe de un asiento debe ser positivo.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("El concepto es obligatorio.");
        }
        concepto = concepto.trim();
        if (concepto.length() > MAXIMO_CONCEPTO) {
            throw new IllegalArgumentException(
                    "El concepto supera los " + MAXIMO_CONCEPTO + " caracteres.");
        }
    }

    /** Lo que suma o resta este asiento al saldo, ya con su signo. */
    public Dinero efecto() {
        return tipo == TipoDeAsiento.ABONO
                ? importe
                : Dinero.cero(importe.moneda()).menos(importe);
    }
}
