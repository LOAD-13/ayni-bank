package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/recuperacion}. El formato del correo lo valida el dominio;
 * un correo mal escrito da 400 tenga o no cuenta, asi que no revela nada.
 */
public record SolicitudDeRecuperacionDto(
        @NotBlank(message = "El correo electronico es obligatorio.")
        @Size(max = 254, message = "El correo electronico supera la longitud maxima.")
        String correo) {

    @Override
    public String toString() {
        return "SolicitudDeRecuperacionDto[oculto]";
    }
}
