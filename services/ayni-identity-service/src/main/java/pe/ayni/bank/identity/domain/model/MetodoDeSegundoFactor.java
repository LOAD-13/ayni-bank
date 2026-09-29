package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa la configuración y estado de inscripción de un método de segundo factor para un usuario.
 *
 * <p>Soporta métodos como {@link TipoDeSegundoFactor#APP_AUTENTICADORA},
 * {@link TipoDeSegundoFactor#CORREO_ELECTRONICO} y {@link TipoDeSegundoFactor#SMS}.
 */
public final class MetodoDeSegundoFactor {

    private final UUID id;
    private final UUID usuarioId;
    private final TipoDeSegundoFactor tipo;
    private final String secreto;
    private final Instant creadoEn;
    private final Instant confirmadoEn;

    private MetodoDeSegundoFactor(UUID id, UUID usuarioId, TipoDeSegundoFactor tipo,
                                  String secreto, Instant creadoEn, Instant confirmadoEn) {
        this.id = Objects.requireNonNull(id, "El id no puede ser nulo");
        this.usuarioId = Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        this.tipo = Objects.requireNonNull(tipo, "El tipo no puede ser nulo");
        this.secreto = secreto;
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn no puede ser nulo");
        this.confirmadoEn = confirmadoEn;
    }

    /** Crea un registro de método en estado pendiente de confirmación. */
    public static MetodoDeSegundoFactor inscribir(UUID id, UUID usuarioId, TipoDeSegundoFactor tipo,
                                                  String secreto, Instant momento) {
        return new MetodoDeSegundoFactor(id, usuarioId, tipo, secreto, momento, null);
    }

    /** Reconstituye un método guardado previamente en la base de datos. */
    public static MetodoDeSegundoFactor reconstituir(UUID id, UUID usuarioId, TipoDeSegundoFactor tipo,
                                                     String secreto, Instant creadoEn, Instant confirmadoEn) {
        return new MetodoDeSegundoFactor(id, usuarioId, tipo, secreto, creadoEn, confirmadoEn);
    }

    /** Marca el método como verificado/confirmado tras validar el primer código o desafío. */
    public MetodoDeSegundoFactor confirmar(Instant momento) {
        if (estaConfirmado()) {
            return this;
        }
        return new MetodoDeSegundoFactor(id, usuarioId, tipo, secreto, creadoEn, momento);
    }

    public boolean estaConfirmado() {
        return confirmadoEn != null;
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public TipoDeSegundoFactor tipo() {
        return tipo;
    }

    public String secreto() {
        return secreto;
    }

    public Instant creadoEn() {
        return creadoEn;
    }

    public Instant confirmadoEn() {
        return confirmadoEn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetodoDeSegundoFactor that = (MetodoDeSegundoFactor) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MetodoDeSegundoFactor[id=" + id + ", usuarioId=" + usuarioId + ", tipo=" + tipo + ", confirmado=" + estaConfirmado() + "]";
    }
}
