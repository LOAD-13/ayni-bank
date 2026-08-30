package pe.ayni.bank.identity.infrastructure.in.web;

import java.time.LocalDate;
import java.time.Period;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/registro}. Corresponde al esquema
 * {@code SolicitudRegistro} de {@code contracts/identity-service.openapi.yaml}.
 *
 * <p>Las anotaciones de validacion <strong>no sustituyen a las reglas del dominio</strong>,
 * las adelantan: rechazan lo malformado antes de gastar una transaccion y devuelven todos
 * los errores de campo a la vez, que es lo que la interfaz necesita para marcarlos juntos.
 * La regla que manda sigue siendo la del dominio, y se aplica igual aunque manana entre
 * una peticion por otra via.
 *
 * <p>La politica de contrasena no se anota aqui a proposito: vive en
 * {@code PoliticaDeContrasena} porque es negocio, no formato.
 *
 * <p>El formato del numero de documento tampoco: depende del tipo, y esa correspondencia
 * la conoce {@code TipoDocumento}. Aqui solo se comprueba que venga y que quepa.
 */
public record SolicitudDeRegistroDto(

        @NotBlank(message = "Escribe tus nombres.")
        @Size(max = 80, message = "Los nombres superan la longitud maxima.")
        String nombres,

        @NotBlank(message = "Escribe tus apellidos.")
        @Size(max = 120, message = "Los apellidos superan la longitud maxima.")
        String apellidos,

        @NotBlank(message = "El tipo de documento es obligatorio.")
        @Pattern(regexp = "DNI|CE|PASAPORTE",
                 message = "El tipo de documento debe ser DNI, CE o PASAPORTE.")
        String tipoDocumento,

        @NotBlank(message = "El numero de documento es obligatorio.")
        @Size(max = 20, message = "El numero de documento supera la longitud maxima.")
        String numeroDocumento,

        @NotNull(message = "Indica tu fecha de nacimiento.")
        @Past(message = "La fecha de nacimiento no puede ser futura.")
        LocalDate fechaNacimiento,

        @NotBlank(message = "El correo electronico es obligatorio.")
        @Email(message = "El correo electronico no tiene un formato valido.")
        @Size(max = 254, message = "El correo electronico supera la longitud maxima.")
        String correo,

        @NotBlank(message = "El numero de celular es obligatorio.")
        @Pattern(regexp = "^9[0-9]{8}$",
                 message = "El celular debe tener nueve digitos y empezar en 9.")
        String celular,

        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 12, max = 128,
              message = "La contrasena debe tener entre 12 y 128 caracteres.")
        String contrasena,

        @AssertTrue(message = "Debes aceptar los terminos y la politica de datos personales.")
        boolean aceptaTerminos) {

    /** Edad minima para abrir una cuenta a nombre propio en el Peru. */
    private static final int EDAD_MINIMA = 18;

    /**
     * Adelanta la comprobacion de mayoria de edad para que el error salga marcado sobre el
     * campo, junto a los demas, en lugar de llegar suelto desde el dominio.
     *
     * <p>{@code @JsonIgnore} porque, sin el, Jackson interpreta este metodo como una
     * propiedad {@code mayorDeEdad} y la exige en el cuerpo de la peticion.
     */
    @JsonIgnore
    @AssertTrue(message = "Debes tener al menos 18 anos para abrir una cuenta.")
    public boolean isMayorDeEdad() {
        // Nulo se deja pasar: de ese campo ya se queja @NotNull, y encadenar dos mensajes
        // sobre el mismo hueco vacio solo confunde.
        if (fechaNacimiento == null || fechaNacimiento.isAfter(LocalDate.now())) {
            return true;
        }
        return Period.between(fechaNacimiento, LocalDate.now()).getYears() >= EDAD_MINIMA;
    }

    /** Ni un dato personal: este DTO no deja rastro en ningun log. */
    @Override
    public String toString() {
        return "SolicitudDeRegistroDto[aceptaTerminos=" + aceptaTerminos + "]";
    }
}
