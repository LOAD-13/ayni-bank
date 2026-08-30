package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/**
 * Datos que llegan del formulario de registro, sin interpretar todavia.
 *
 * <p>Lleva la contrasena en claro porque es el unico punto del sistema donde tiene que
 * viajar: entra por aqui, se evalua contra la politica, se deriva con Argon2id y no vuelve
 * a existir. {@code toString()} esta sobrescrito para que ese trayecto no se convierta en
 * una linea de log.
 *
 * <p>Vive en {@code domain.model} y no en {@code domain.port.in} porque ArchUnit exige que
 * todo lo que resida en ese paquete se llame {@code ...UseCase}, y esto no es un caso de
 * uso sino su entrada.
 */
public record ComandoDeRegistro(String correo, String celular, String contrasena,
                                boolean aceptaTerminos) {

    public ComandoDeRegistro {
        Objects.requireNonNull(correo, "El correo es obligatorio.");
        Objects.requireNonNull(celular, "El celular es obligatorio.");
        Objects.requireNonNull(contrasena, "La contrasena es obligatoria.");
    }

    @Override
    public String toString() {
        return "ComandoDeRegistro[correo=oculto, celular=oculto, contrasena=oculta, "
                + "aceptaTerminos=" + aceptaTerminos + "]";
    }
}
