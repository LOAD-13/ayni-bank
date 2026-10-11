package pe.ayni.bank.identity.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una peticion de confirmar una operacion con el segundo factor (HU-07, ADR-0031).
 *
 * <p>Vive cinco minutos, admite tres intentos y se usa una vez. Guarda la huella de la
 * operacion, no la operacion: identity no necesita saber a quien se transfiere ni cuanto,
 * solo garantizar que el token que emite sirve para esa transferencia y para ninguna otra.
 */
public final class ConfirmacionDeOperacion {

    public static final Duration VIGENCIA = Duration.ofMinutes(5);
    public static final int MAXIMO_INTENTOS = 3;

    private final UUID id;
    private final UUID usuarioId;
    private final String huella;
    private final TipoDeSegundoFactor metodo;
    /** El desafio por codigo enviado al correo; nulo si el metodo es la app autenticadora. */
    private final UUID desafioCodigoId;
    private final int intentosFallidos;
    private final Instant creadaEn;
    private final Instant expiraEn;
    private final Instant usadaEn;

    @SuppressWarnings("java:S107") // Reconstitucion desde la base: un parametro por columna.
    private ConfirmacionDeOperacion(UUID id, UUID usuarioId, String huella, TipoDeSegundoFactor metodo,
                                    UUID desafioCodigoId, int intentosFallidos, Instant creadaEn,
                                    Instant expiraEn, Instant usadaEn) {
        this.id = Objects.requireNonNull(id);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.huella = Objects.requireNonNull(huella);
        this.metodo = Objects.requireNonNull(metodo);
        this.desafioCodigoId = desafioCodigoId;
        this.intentosFallidos = intentosFallidos;
        this.creadaEn = Objects.requireNonNull(creadaEn);
        this.expiraEn = Objects.requireNonNull(expiraEn);
        this.usadaEn = usadaEn;
    }

    public static ConfirmacionDeOperacion abrir(UUID id, UUID usuarioId, String huella,
                                                TipoDeSegundoFactor metodo, UUID desafioCodigoId,
                                                Instant momento) {
        return new ConfirmacionDeOperacion(id, usuarioId, huella, metodo, desafioCodigoId, 0,
                momento, momento.plus(VIGENCIA), null);
    }

    @SuppressWarnings("java:S107")
    public static ConfirmacionDeOperacion reconstituir(UUID id, UUID usuarioId, String huella,
                                                       TipoDeSegundoFactor metodo, UUID desafioCodigoId,
                                                       int intentosFallidos, Instant creadaEn,
                                                       Instant expiraEn, Instant usadaEn) {
        return new ConfirmacionDeOperacion(id, usuarioId, huella, metodo, desafioCodigoId,
                intentosFallidos, creadaEn, expiraEn, usadaEn);
    }

    /** Sirve si no se uso, no caduco y le quedan intentos. */
    public boolean estaVigente(Instant momento) {
        return usadaEn == null && momento.isBefore(expiraEn) && intentosFallidos < MAXIMO_INTENTOS;
    }

    public ConfirmacionDeOperacion registrarFallo() {
        return new ConfirmacionDeOperacion(id, usuarioId, huella, metodo, desafioCodigoId,
                intentosFallidos + 1, creadaEn, expiraEn, usadaEn);
    }

    public ConfirmacionDeOperacion usar(Instant momento) {
        if (!estaVigente(momento)) {
            throw new ConfirmacionNoDisponibleException();
        }
        return new ConfirmacionDeOperacion(id, usuarioId, huella, metodo, desafioCodigoId,
                intentosFallidos, creadaEn, expiraEn, momento);
    }

    public int intentosRestantes() {
        return Math.max(0, MAXIMO_INTENTOS - intentosFallidos);
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

    public TipoDeSegundoFactor metodo() {
        return metodo;
    }

    public UUID desafioCodigoId() {
        return desafioCodigoId;
    }

    public int intentosFallidos() {
        return intentosFallidos;
    }

    public Instant creadaEn() {
        return creadaEn;
    }

    public Instant expiraEn() {
        return expiraEn;
    }

    public Instant usadaEn() {
        return usadaEn;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof ConfirmacionDeOperacion c && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ConfirmacionDeOperacion[id=" + id + ", metodo=" + metodo + ", intentos=" + intentosFallidos + "]";
    }
}
