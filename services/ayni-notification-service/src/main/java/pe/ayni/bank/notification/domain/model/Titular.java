package pe.ayni.bank.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A quien se escribe. Es la proyeccion local de lo que publica identity. */
public record Titular(UUID usuarioId, String correo, String nombrePila) {

    public Titular {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("El titular necesita un correo");
        }
        nombrePila = nombrePila == null ? "" : nombrePila.strip();
    }

    /** El correo no sale en ninguna traza. */
    @Override
    public String toString() {
        return "Titular[usuarioId=" + usuarioId + "]";
    }
}
