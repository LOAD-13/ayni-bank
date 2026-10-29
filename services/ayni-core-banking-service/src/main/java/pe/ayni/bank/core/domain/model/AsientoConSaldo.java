package pe.ayni.bank.core.domain.model;

import java.util.Objects;

/**
 * Un asiento del libro mayor junto con el saldo resultante de la cuenta inmediatamente después
 * de aplicarlo · HU-08.
 */
public record AsientoConSaldo(Asiento asiento, Dinero saldoPosterior) {

    public AsientoConSaldo {
        Objects.requireNonNull(asiento, "El asiento es obligatorio.");
        Objects.requireNonNull(saldoPosterior, "El saldo posterior es obligatorio.");
    }
}
