package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * El tipo de cambio que se le aplica al cliente en una conversion (HU-12): soles por dolar, ya
 * con el spread del banco, cuando se obtuvo de la fuente oficial y si vino de la cache porque la
 * fuente no respondio. Queda guardado junto a la conversion para poder auditarla.
 */
public record TipoDeCambioAplicado(BigDecimal solesPorDolar, Instant obtenidoEn, boolean enCache) {

    public TipoDeCambioAplicado {
        Objects.requireNonNull(solesPorDolar, "solesPorDolar");
        Objects.requireNonNull(obtenidoEn, "obtenidoEn");
        if (solesPorDolar.signum() <= 0) {
            throw new IllegalArgumentException("El tipo de cambio debe ser positivo");
        }
        // La escala de la columna conversion.tipo_aplicado: lo que se usa para convertir es
        // exactamente lo que se guarda, y el escenario 3 recalcula el mismo importe.
        solesPorDolar = solesPorDolar.setScale(ESCALA, Dinero.REDONDEO);
    }

    /** Decimales del tipo de cambio, los de {@code NUMERIC(19,6)}. */
    public static final int ESCALA = 6;

    /** Lo que recibe el cliente en la otra moneda, redondeado a centimos con HALF_EVEN. */
    public Dinero convertir(Dinero importe) {
        return importe.moneda() == Moneda.PEN
                ? new Dinero(importe.importe().divide(solesPorDolar, Dinero.DECIMALES, Dinero.REDONDEO), Moneda.USD)
                : new Dinero(importe.importe().multiply(solesPorDolar), Moneda.PEN);
    }
}
