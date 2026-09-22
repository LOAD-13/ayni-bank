package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Desafío de verificación mediante código de un solo uso (OTP).
 *
 * <p>Reglas de negocio exigidas por HU-22:
 * <ul>
 *   <li>Caducidad a los 10 minutos ({@link #VIGENCIA}).</li>
 *   <li>Almacenamiento exclusivo del hash SHA-256 del código (nunca en claro).</li>
 *   <li>Límite de máximo 3 intentos fallidos de verificación ({@link #MAX_INTENTOS}).</li>
 * </ul>
 */
public final class DesafioPorCodigo {

    /** Vigencia de 10 minutos para códigos OTP por correo/SMS exigida por HU-22. */
    public static final Duration VIGENCIA = Duration.ofMinutes(10);

    /** Límite de 3 intentos fallidos de verificación. */
    public static final int MAX_INTENTOS = 3;

    private final UUID id;
    private final UUID usuarioId;
    private final TipoDeSegundoFactor tipoFactor;
    private final String hashCodigo;
    private final int intentosRealizados;
    private final Instant creadoEn;
    private final Instant expiraEn;
    private final Instant verificadoEn;

    @SuppressWarnings("java:S107")
    private DesafioPorCodigo(UUID id, UUID usuarioId, TipoDeSegundoFactor tipoFactor,
                             String hashCodigo, int intentosRealizados,
                             Instant creadoEn, Instant expiraEn, Instant verificadoEn) {
        this.id = Objects.requireNonNull(id, "El id no puede ser nulo");
        this.usuarioId = Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        this.tipoFactor = Objects.requireNonNull(tipoFactor, "El tipoFactor no puede ser nulo");
        this.hashCodigo = Objects.requireNonNull(hashCodigo, "El hashCodigo no puede ser nulo");
        this.intentosRealizados = intentosRealizados;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn no puede ser nulo");
        this.expiraEn = Objects.requireNonNull(expiraEn, "expiraEn no puede ser nulo");
        this.verificadoEn = verificadoEn;
    }

    /** Genera un nuevo desafío OTP con 10 minutos de vigencia y 0 intentos. */
    public static DesafioPorCodigo generar(UUID id, UUID usuarioId, TipoDeSegundoFactor tipoFactor,
                                          String hashCodigo, Instant momento) {
        return new DesafioPorCodigo(id, usuarioId, tipoFactor, hashCodigo, 0,
                momento, momento.plus(VIGENCIA), null);
    }

    /** Reconstituye un desafío almacenado previamente. */
    @SuppressWarnings("java:S107")
    public static DesafioPorCodigo reconstituir(UUID id, UUID usuarioId, TipoDeSegundoFactor tipoFactor,
                                                String hashCodigo, int intentosRealizados,
                                                Instant creadoEn, Instant expiraEn, Instant verificadoEn) {
        return new DesafioPorCodigo(id, usuarioId, tipoFactor, hashCodigo, intentosRealizados,
                creadoEn, expiraEn, verificadoEn);
    }

    /** Determina si el desafío ha superado su tiempo de vigencia. */
    public boolean estaExpirado(Instant momento) {
        return momento.isAfter(expiraEn);
    }

    /** Determina si el desafío ya fue completado exitosamente. */
    public boolean estaVerificado() {
        return verificadoEn != null;
    }

    /** Determina si se ha alcanzado o superado el máximo de 3 intentos. */
    public boolean alcanzoMaximoIntentos() {
        return intentosRealizados >= MAX_INTENTOS;
    }

    /** Incrementa el contador de intentos fallidos. */
    public DesafioPorCodigo registrarIntentoFallido() {
        if (estaVerificado()) {
            return this;
        }
        return new DesafioPorCodigo(id, usuarioId, tipoFactor, hashCodigo,
                intentosRealizados + 1, creadoEn, expiraEn, verificadoEn);
    }

    /** Marca el desafío como verificado correctamente. */
    public DesafioPorCodigo marcarVerificado(Instant momento) {
        if (estaVerificado()) {
            return this;
        }
        return new DesafioPorCodigo(id, usuarioId, tipoFactor, hashCodigo,
                intentosRealizados, creadoEn, expiraEn, momento);
    }

    /** Invalida el desafío forzando el máximo de intentos. */
    public DesafioPorCodigo invalidar() {
        return new DesafioPorCodigo(id, usuarioId, tipoFactor, hashCodigo,
                MAX_INTENTOS, creadoEn, expiraEn, verificadoEn);
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public TipoDeSegundoFactor tipoFactor() {
        return tipoFactor;
    }

    public String hashCodigo() {
        return hashCodigo;
    }

    public int intentosRealizados() {
        return intentosRealizados;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public Instant expiraEn() {
        return expiraEn;
    }

    public Instant verificadoEn() {
        return verificadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DesafioPorCodigo that = (DesafioPorCodigo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DesafioPorCodigo[id=" + id + ", usuarioId=" + usuarioId + ", tipo=" + tipoFactor + ", intentos=" + intentosRealizados + ", verificado=" + estaVerificado() + "]";
    }
}
