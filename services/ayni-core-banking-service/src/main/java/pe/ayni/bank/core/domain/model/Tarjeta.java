package pe.ayni.bank.core.domain.model;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * La tarjeta de debito virtual del titular, asociada a su cuenta de ahorro · HU-09.
 *
 * <p>Nace ACTIVA y con vigencia de cuatro anos: vence el ultimo dia del mes de emision cuatro
 * anos despues, en hora de Lima, como las tarjetas fisicas. El CVV no forma parte de la
 * tarjeta: no se guarda en ningun sitio, se calcula cuando se pide (ver CalculadoraDeCvv).
 */
public record Tarjeta(UUID id, UUID cuentaId, UUID usuarioId, Pan pan, YearMonth venceEn,
                      EstadoDeTarjeta estado, Instant emitidaEn) {

    public static final int ANOS_DE_VIGENCIA = 4;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public Tarjeta {
        Objects.requireNonNull(id);
        Objects.requireNonNull(cuentaId);
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(pan);
        Objects.requireNonNull(venceEn);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(emitidaEn);
    }

    public static Tarjeta emitir(UUID cuentaId, UUID usuarioId, Pan pan, Instant ahora) {
        YearMonth vence = YearMonth.from(ahora.atZone(LIMA)).plusYears(ANOS_DE_VIGENCIA);
        return new Tarjeta(UUID.randomUUID(), cuentaId, usuarioId, pan, vence, EstadoDeTarjeta.ACTIVA, ahora);
    }

    /** Como se imprime en la tarjeta: {@code 10/30}. */
    public String vencimiento() {
        return "%02d/%02d".formatted(venceEn.getMonthValue(), venceEn.getYear() % 100);
    }
}
