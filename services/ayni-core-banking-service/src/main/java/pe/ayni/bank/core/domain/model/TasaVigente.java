package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del catalogo {@code tasa_producto}: la TEA y la TREA de un producto desde una fecha.
 * El devengo guarda su identificador para que siempre se sepa con que tasa se calculo.
 */
public record TasaVigente(long id, BigDecimal tea, BigDecimal trea, LocalDate vigenteDesde) {
}
