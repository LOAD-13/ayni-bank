package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** La foto de un lado del DNI que el navegador acaba de subir. */
public record SolicitudDeEvaluacionDto(
        @NotBlank @Pattern(regexp = "ANVERSO|REVERSO") String tipoDocumento,
        @NotBlank @Size(max = 255) String claveDeObjeto) {
}
