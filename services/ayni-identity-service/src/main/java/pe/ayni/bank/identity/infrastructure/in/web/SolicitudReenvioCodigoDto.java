package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotNull;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** DTO de solicitud para solicitar el reenvío de un código de verificación de contacto. */
public record SolicitudReenvioCodigoDto(
        @NotNull(message = "El tipo de factor de contacto es obligatorio")
        TipoDeSegundoFactor tipoFactor
) {}
