package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/recuperacion/validacion}.
 *
 * <p>El token llega en el cuerpo y no en la URL: una URL acaba en los registros de acceso.
 * El tope de longitud evita calcular huellas de cadenas arbitrariamente largas.
 */
public record EnlaceDeRecuperacionDto(
        @NotBlank(message = "El enlace no es valido.")
        @Size(max = 128, message = "El enlace no es valido.")
        String token) {

    @Override
    public String toString() {
        return "EnlaceDeRecuperacionDto[oculto]";
    }
}
