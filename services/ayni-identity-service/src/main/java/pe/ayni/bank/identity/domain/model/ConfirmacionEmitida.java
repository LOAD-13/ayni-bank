package pe.ayni.bank.identity.domain.model;

import java.time.Instant;

/** HU-07: el token de confirmacion para core-banking y cuando caduca. */
public record ConfirmacionEmitida(String token, Instant expiraEn) {

    /** El token no sale en ninguna traza. */
    @Override
    public String toString() {
        return "ConfirmacionEmitida[oculto]";
    }
}
