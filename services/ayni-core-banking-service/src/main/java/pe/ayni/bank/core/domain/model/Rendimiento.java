package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que ve el titular de su rendimiento.
 *
 * @param devengadoDelMes lo ganado en el mes en curso y aun no abonado, con 8 decimales
 * @param abonadoTotal    todo lo capitalizado desde la apertura
 * @param devengos        los ultimos devengos, cada uno con la tasa con que se calculo
 */
public record Rendimiento(TasaVigente tasa, Moneda moneda, BigDecimal devengadoDelMes, Dinero abonadoTotal,
                          List<Devengo> devengos) {
}
