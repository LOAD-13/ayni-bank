package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token del enlace de recuperacion de contrasena (HU-21).
 *
 * <p>Sigue las mismas reglas que {@link RefreshToken}: el valor en claro solo viaja en el
 * correo y lo que se persiste es su huella SHA-256. Una base filtrada no entrega enlaces
 * con los que cambiar contrasenas ajenas.
 *
 * <p>Sirve <strong>una sola vez</strong> y durante <strong>treinta minutos</strong>. Un
 * enlace de recuperacion es una llave de la cuenta que queda escrita en una bandeja de
 * entrada; cuanto menos dure y menos veces abra, menos vale si alguien llega a ese correo.
 */
public final class TokenDeRecuperacion {

    /** Vigencia exigida por HU-21. */
    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    private final UUID id;
    private final UUID usuarioId;
    private final String huella;
    private final Instant emitidoEn;
    private final Instant expiraEn;
    private final Instant usadoEn;

    private TokenDeRecuperacion(UUID id, UUID usuarioId, String huella,
                                Instant emitidoEn, Instant expiraEn, Instant usadoEn) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.huella = Objects.requireNonNull(huella);
        this.emitidoEn = Objects.requireNonNull(emitidoEn);
        this.expiraEn = Objects.requireNonNull(expiraEn);
        this.usadoEn = usadoEn;
    }

    public static TokenDeRecuperacion emitir(UUID id, UUID usuarioId, String huella,
                                             Instant momento) {
        return new TokenDeRecuperacion(id, usuarioId, huella, momento,
                momento.plus(VIGENCIA), null);
    }

    public static TokenDeRecuperacion reconstituir(UUID id, UUID usuarioId, String huella,
                                                   Instant emitidoEn, Instant expiraEn,
                                                   Instant usadoEn) {
        return new TokenDeRecuperacion(id, usuarioId, huella, emitidoEn, expiraEn, usadoEn);
    }

    /**
     * Gasta el token.
     *
     * @throws EnlaceDeRecuperacionInvalidoException si ya se uso o caduco
     */
    public TokenDeRecuperacion usar(Instant momento) {
        if (!estaVigente(momento)) {
            throw new EnlaceDeRecuperacionInvalidoException();
        }
        return new TokenDeRecuperacion(id, usuarioId, huella, emitidoEn, expiraEn, momento);
    }

    public boolean estaVigente(Instant momento) {
        return usadoEn == null && momento.isBefore(expiraEn);
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public String huella() {
        return huella;
    }

    public Instant emitidoEn() {
        return emitidoEn;
    }

    public Instant expiraEn() {
        return expiraEn;
    }

    public Instant usadoEn() {
        return usadoEn;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof TokenDeRecuperacion token && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Sin la huella: no tiene por que salir en una traza. */
    @Override
    public String toString() {
        return "TokenDeRecuperacion[id=" + id + ", usado=" + (usadoEn != null) + "]";
    }
}
