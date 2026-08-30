package pe.ayni.bank.identity.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * La contrasena incumple la politica. Escenario 3 de HU-01.
 *
 * <p>Lleva la lista completa de requisitos incumplidos, no solo el primero: la interfaz los
 * muestra todos a la vez en lugar de obligar a la persona a descubrirlos por ensayo y error.
 *
 * <p>El mensaje de la excepcion enumera los requisitos, nunca la contrasena.
 */
public class ContrasenaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<RequisitoDeContrasena> incumplidos;

    public ContrasenaInvalidaException(List<RequisitoDeContrasena> incumplidos) {
        super(construirMensaje(incumplidos));
        this.incumplidos = List.copyOf(incumplidos);
    }

    public List<RequisitoDeContrasena> incumplidos() {
        return incumplidos;
    }

    private static String construirMensaje(List<RequisitoDeContrasena> incumplidos) {
        Objects.requireNonNull(incumplidos, "La lista de requisitos incumplidos es obligatoria.");
        if (incumplidos.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se lanza esta excepcion sin requisitos incumplidos.");
        }
        StringBuilder mensaje = new StringBuilder("La contrasena no cumple la politica:");
        for (RequisitoDeContrasena requisito : incumplidos) {
            mensaje.append(' ').append(requisito.mensaje());
        }
        return mensaje.toString();
    }
}
