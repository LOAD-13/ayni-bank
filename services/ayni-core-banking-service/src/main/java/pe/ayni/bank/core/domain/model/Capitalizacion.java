package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.UUID;

/**
 * El cierre de intereses de un mes: lo devengado se abona en la cuenta.
 *
 * <p>Los devengos tienen 8 decimales y la cuenta solo 2. Lo que no llega a centimo no se
 * pierde ni se regala: pasa como residuo al mes siguiente. Asi, a lo largo de la vida de la
 * cuenta, lo abonado difiere de lo devengado en menos de medio centimo. Ver ADR-0032.
 *
 * @param residuoAnterior la fraccion que dejo el mes anterior; puede ser negativa si el
 *                        redondeo de entonces abono medio centimo de mas
 * @param movimientoId    nulo si no hubo nada que abonar
 */
public record Capitalizacion(UUID id, UUID cuentaId, YearMonth periodo, BigDecimal devengado,
                             BigDecimal residuoAnterior, Dinero abonado, BigDecimal residuo, UUID movimientoId) {

    public static Capitalizacion de(UUID cuentaId, YearMonth periodo, BigDecimal devengado,
                                    BigDecimal residuoAnterior, Moneda moneda) {
        BigDecimal total = devengado.add(residuoAnterior);
        BigDecimal abonado = total.setScale(Dinero.DECIMALES, RoundingMode.HALF_EVEN).max(BigDecimal.ZERO)
                .setScale(Dinero.DECIMALES, RoundingMode.UNNECESSARY);
        BigDecimal residuo = total.subtract(abonado).setScale(CalculoDeInteres.DECIMALES_DEL_DEVENGO,
                RoundingMode.UNNECESSARY);
        UUID movimiento = abonado.signum() > 0 ? UUID.randomUUID() : null;
        return new Capitalizacion(UUID.randomUUID(), cuentaId, periodo, devengado, residuoAnterior,
                new Dinero(abonado, moneda), residuo, movimiento);
    }

    public boolean hayQueAbonar() {
        return movimientoId != null;
    }
}
