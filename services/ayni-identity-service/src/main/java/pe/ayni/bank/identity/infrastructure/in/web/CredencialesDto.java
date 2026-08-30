package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /api/v1/sesion}.
 *
 * <p><strong>Aqui no se valida el formato de la contrasena.</strong> Es deliberado y va en
 * contra del reflejo habitual. Rechazar una contrasena de ocho caracteres con un 400 antes
 * de mirar nada le dice a quien prueba que ese usuario no puede tener una contrasena
 * corta, y sobre todo distingue esa peticion de otra con contrasena bien formada pero
 * incorrecta, que devuelve 401. La respuesta debe ser la misma en ambos casos.
 */
public record CredencialesDto(

        @NotBlank(message = "El correo electronico es obligatorio.")
        String correo,

        @NotBlank(message = "La contrasena es obligatoria.")
        String contrasena) {

    /** Ni el correo ni la contrasena: este DTO no deja rastro en ningun log. */
    @Override
    public String toString() {
        return "CredencialesDto[oculto]";
    }
}
