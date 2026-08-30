package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de {@code POST /api/v1/sesion/segundo-factor}. */
public record SegundoFactorDto(

        @NotBlank(message = "Falta el identificador del desafio.")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$",
                 message = "El identificador del desafio no es valido.")
        String desafioId,

        @NotBlank(message = "El codigo de verificacion es obligatorio.")
        String codigo) {

    /** El codigo es un secreto de un solo uso. */
    @Override
    public String toString() {
        return "SegundoFactorDto[desafioId=" + desafioId + ", codigo=oculto]";
    }
}
