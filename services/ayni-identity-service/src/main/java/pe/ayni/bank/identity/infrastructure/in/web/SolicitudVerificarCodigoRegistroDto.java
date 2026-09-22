package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** DTO de solicitud para verificar el código OTP del contacto inicial durante el registro. */
public record SolicitudVerificarCodigoRegistroDto(
        @NotNull(message = "El tipo de factor es obligatorio")
        TipoDeSegundoFactor tipoFactor,

        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "El código debe ser de 6 dígitos")
        String codigo
) {}
