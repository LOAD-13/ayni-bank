package pe.ayni.bank.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Lo que devuelve el registro. Es <strong>identico</strong> tanto si se creo un usuario
 * como si el correo ya estaba registrado.
 *
 * <p>Ahi esta el punto entero de este tipo. Si la respuesta variase —un 409, otro mensaje,
 * incluso un tiempo de respuesta distinto— el endpoint se convertiria en un oraculo:
 * cualquiera podria recorrer una lista de correos filtrada de otro sitio y averiguar
 * quienes son clientes de Ayni. Eso es informacion vendible y es el primer paso de una
 * campana de phishing dirigida.
 *
 * <p>El {@code solicitudId} es opaco: no se deriva del correo ni del identificador del
 * usuario, de modo que tampoco delata nada por su contenido.
 *
 * <p>Ver ADR-0008.
 */
public record ResultadoDeRegistro(UUID solicitudId, EstadoUsuario estado, String mensaje) {

    /**
     * Redaccion aprobada en el prototipo. No afirma ni niega que el correo exista.
     */
    public static final String MENSAJE_NEUTRO =
            "Si el correo esta disponible, te enviaremos un enlace de verificacion.";

    public ResultadoDeRegistro {
        Objects.requireNonNull(solicitudId, "El identificador de la solicitud es obligatorio.");
        Objects.requireNonNull(estado, "El estado es obligatorio.");
        Objects.requireNonNull(mensaje, "El mensaje es obligatorio.");
    }

    public static ResultadoDeRegistro aceptado(UUID solicitudId) {
        return new ResultadoDeRegistro(
                solicitudId, EstadoUsuario.PENDIENTE_VERIFICACION, MENSAJE_NEUTRO);
    }
}
