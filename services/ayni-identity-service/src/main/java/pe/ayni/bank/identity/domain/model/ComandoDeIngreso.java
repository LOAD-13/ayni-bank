package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/** Primer paso del ingreso: correo y contrasena, mas de donde llega la peticion. */
public record ComandoDeIngreso(String correo, String contrasena, HuellaDeCliente cliente) {

    public ComandoDeIngreso {
        Objects.requireNonNull(correo, "El correo es obligatorio.");
        Objects.requireNonNull(contrasena, "La contrasena es obligatoria.");
        Objects.requireNonNull(cliente);
    }

    @Override
    public String toString() {
        return "ComandoDeIngreso[correo=oculto, contrasena=oculta]";
    }
}
