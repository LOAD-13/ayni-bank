package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** DTO de solicitud para verificar un código OTP de 6 dígitos. */
public record SolicitudVerificarDesafioDto(
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "El código debe tener exactamente 6 dígitos")
        String codigo
) {}
