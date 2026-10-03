package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;

/** La clave con la que la web subio la selfie usando la URL firmada. */
public record SolicitudDeSelfieDto(@NotBlank String claveDeObjeto) {
}
