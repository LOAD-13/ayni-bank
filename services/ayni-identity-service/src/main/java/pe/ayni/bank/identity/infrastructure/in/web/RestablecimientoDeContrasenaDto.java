package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/recuperacion/confirmacion}. La contrasena se valida contra
 * la politica en el dominio, que devuelve todos los requisitos incumplidos a la vez.
 */
public record RestablecimientoDeContrasenaDto(
        @NotBlank(message = "El enlace no es valido.")
        @Size(max = 128, message = "El enlace no es valido.")
        String token,
        @NotNull(message = "La contrasena nueva es obligatoria.")
        String contrasenaNueva) {

    /** Ni el token ni la contrasena: este DTO no deja rastro en ningun log. */
    @Override
    public String toString() {
        return "RestablecimientoDeContrasenaDto[oculto]";
    }
}
