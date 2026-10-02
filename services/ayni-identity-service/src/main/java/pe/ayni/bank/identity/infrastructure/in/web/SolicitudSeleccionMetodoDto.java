package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotNull;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

public record SolicitudSeleccionMetodoDto(
        @NotNull(message = "El tipo de segundo factor es obligatorio")
        TipoDeSegundoFactor tipo
) {}
