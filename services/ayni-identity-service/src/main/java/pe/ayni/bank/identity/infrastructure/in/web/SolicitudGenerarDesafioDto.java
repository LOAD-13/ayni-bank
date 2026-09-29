package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotNull;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

/** DTO de solicitud para generar un desafío OTP. */
public record SolicitudGenerarDesafioDto(
        @NotNull(message = "El tipo de segundo factor no puede ser nulo")
        TipoDeSegundoFactor tipoFactor
) {}
