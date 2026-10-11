package pe.ayni.bank.core.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * El interes que gano una cuenta en un dia. Lleva el saldo base y la tasa aplicada para que
 * cualquiera pueda rehacer la cuenta: es la pista que exige la HU-06.
 */
public record Devengo(UUID id, UUID cuentaId, LocalDate fecha, Dinero saldoBase, long tasaId, BigDecimal tea,
                      BigDecimal importe, Instant registradoEn) {

    /**
     * El devengo del dia, o nada si la cuenta cerro sin saldo: sin saldo no hay interes que
     * registrar y la cuenta queda exactamente como estaba (escenario 4).
     */
    public static Optional<Devengo> calcular(UUID cuentaId, LocalDate fecha, Dinero saldoDeCierre,
                                             TasaVigente tasa, Instant ahora) {
        if (!saldoDeCierre.esPositivo()) {
            return Optional.empty();
        }
        return Optional.of(new Devengo(UUID.randomUUID(), cuentaId, fecha, saldoDeCierre, tasa.id(), tasa.tea(),
                CalculoDeInteres.interesDelDia(saldoDeCierre.importe(), tasa.tea()), ahora));
    }
}
