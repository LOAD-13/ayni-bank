package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de renovación, rotativo y agrupado en familias.
 *
 * <p><strong>Qué es una familia.</strong> Cada inicio de sesión abre una familia. Cada vez
 * que el token se usa para renovar, se consume y nace otro de la misma familia. Así, la
 * familia es la sesión: un dispositivo, un navegador, una vez que alguien entró.
 *
 * <p><strong>Para qué sirve.</strong> Un token de renovación vive siete días y viaja en una
 * cookie; si alguien lo roba, tiene siete días de acceso. La rotación por sí sola no lo
 * impide, pero sí lo delata: cuando el ladrón usa el token, el legítimo deja de valer, y
 * cuando el titular vuelve a renovar presenta un token ya consumido. Eso no puede pasar en
 * un uso normal —cada token se usa exactamente una vez—, así que es señal inequívoca de que
 * hay una copia por ahí. La respuesta es invalidar la familia entera: se cae la sesión del
 * ladrón y la del titular, y el titular vuelve a entrar con su contraseña y su segundo
 * factor, que el ladrón no tiene. Es el escenario 4 de HU-04.
 *
 * <p><strong>Nunca se guarda el token.</strong> Lo que se persiste es su huella SHA-256. Una
 * base de datos filtrada no entrega sesiones activas, del mismo modo que no entrega
 * contraseñas. Aquí basta SHA-256 y no hace falta Argon2id: el valor es aleatorio de 256
 * bits, no una palabra que alguien pueda adivinar con un diccionario.
 */
public final class RefreshToken {

    /** Vigencia exigida por HU-04. */
    public static final Duration VIGENCIA = Duration.ofDays(7);

    private final UUID id;
    private final UUID familiaId;
    private final UUID usuarioId;
    private final String huella;
    private final Instant emitidoEn;
    private final Instant expiraEn;
    private final Instant consumidoEn;

    private RefreshToken(UUID id, UUID familiaId, UUID usuarioId, String huella,
                         Instant emitidoEn, Instant expiraEn, Instant consumidoEn) {
        this.id = Objects.requireNonNull(id);
        this.familiaId = Objects.requireNonNull(familiaId);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.huella = Objects.requireNonNull(huella);
        this.emitidoEn = Objects.requireNonNull(emitidoEn);
        this.expiraEn = Objects.requireNonNull(expiraEn);
        this.consumidoEn = consumidoEn;
    }

    /** Primer token de una familia nueva: alguien acaba de iniciar sesión. */
    public static RefreshToken abrirFamilia(UUID id, UUID usuarioId, String huella,
                                            Instant momento) {
        return new RefreshToken(id, UUID.randomUUID(), usuarioId, huella,
                momento, momento.plus(VIGENCIA), null);
    }

    public static RefreshToken reconstituir(UUID id, UUID familiaId, UUID usuarioId,
                                            String huella, Instant emitidoEn,
                                            Instant expiraEn, Instant consumidoEn) {
        return new RefreshToken(id, familiaId, usuarioId, huella, emitidoEn, expiraEn,
                consumidoEn);
    }

    /**
     * Consume este token y emite el siguiente de la familia.
     *
     * <p>El sucesor hereda la caducidad, no la reinicia. Si cada renovación empezara a
     * contar de nuevo, una sesión renovada cada seis días no caducaría jamás y los siete
     * días del criterio de aceptación serían decorativos.
     */
    public Rotacion rotar(UUID idDelSucesor, String huellaDelSucesor, Instant momento) {
        if (estaConsumido()) {
            throw new ReutilizacionDeRefreshTokenException(familiaId);
        }
        if (haCaducado(momento)) {
            throw new SesionExpiradaException();
        }

        RefreshToken consumido = new RefreshToken(id, familiaId, usuarioId, huella,
                emitidoEn, expiraEn, momento);
        RefreshToken sucesor = new RefreshToken(idDelSucesor, familiaId, usuarioId,
                huellaDelSucesor, momento, expiraEn, null);

        return new Rotacion(consumido, sucesor);
    }

    public boolean estaConsumido() {
        return consumidoEn != null;
    }

    public boolean haCaducado(Instant momento) {
        return !momento.isBefore(expiraEn);
    }

    public boolean estaVigente(Instant momento) {
        return !estaConsumido() && !haCaducado(momento);
    }

    public UUID id() {
        return id;
    }

    public UUID familiaId() {
        return familiaId;
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

    public Instant consumidoEn() {
        return consumidoEn;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof RefreshToken token && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Sin la huella: identifica una sesión activa y no tiene por qué salir en una traza. */
    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", familia=" + familiaId
                + ", consumido=" + estaConsumido() + "]";
    }

    /** El par que resulta de rotar: el que se acaba de gastar y el que lo sustituye. */
    public record Rotacion(RefreshToken consumido, RefreshToken sucesor) {
    }
}
