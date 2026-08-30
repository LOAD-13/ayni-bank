package pe.ayni.bank.identity.infrastructure.in.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
 */
public record SolicitudDeRegistroDto(

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

    /** Sin correo, sin celular y sin contrasena: este DTO no deja rastro en ningun log. */
    @Override
    public String toString() {
        return "SolicitudDeRegistroDto[aceptaTerminos=" + aceptaTerminos + "]";
    }
}
