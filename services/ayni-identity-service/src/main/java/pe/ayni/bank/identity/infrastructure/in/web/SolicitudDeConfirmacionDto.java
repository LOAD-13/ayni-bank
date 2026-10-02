package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import pe.ayni.bank.identity.domain.model.DatosConfirmados;

/**
 * Los datos del DNI tal como los confirma (o corrige) su titular.
 *
 * <p>{@code numero} es opcional: vacio conserva el que se leyo, que la pantalla solo muestra
 * enmascarado. La fecha de emision es obligatoria aqui aunque el OCR pueda no encontrarla:
 * el DNI siempre la trae impresa y el titular puede leerla.
 */
public record SolicitudDeConfirmacionDto(
        @Pattern(regexp = "^\\d{8}$", message = "El DNI debe tener ocho digitos.") String numero,
        @NotBlank @Size(max = 80) String nombres,
        @NotBlank @Size(max = 120) String apellidos,
        @NotNull @Past LocalDate fechaNacimiento,
        @NotBlank @Pattern(regexp = "M|F", message = "El sexo debe ser M o F.") String sexo,
        @NotNull @PastOrPresent LocalDate fechaEmision) {

    DatosConfirmados aDominio() {
        return new DatosConfirmados(numero, nombres, apellidos, fechaNacimiento, sexo, fechaEmision);
    }

    /** Ni un dato personal: este DTO no deja rastro en ningun log. */
    @Override
    public String toString() {
        return "SolicitudDeConfirmacionDto[oculto]";
    }
}
