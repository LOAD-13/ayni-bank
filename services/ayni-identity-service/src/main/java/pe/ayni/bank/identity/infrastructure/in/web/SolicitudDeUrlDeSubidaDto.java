package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** La misma lista de extensiones que acepta {@code GenerarUrlDeSubidaService}: sin PDF. */
public record SolicitudDeUrlDeSubidaDto(
        @NotBlank @Pattern(regexp = "ANVERSO|REVERSO|SELFIE") String tipoDocumento,
        @NotBlank @Pattern(regexp = "jpg|jpeg|png|webp") String extension) {
}
