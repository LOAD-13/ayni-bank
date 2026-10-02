package pe.ayni.bank.identity.domain.model;

import java.time.LocalDate;

/**
 * Lo que la persona declaro en el paso 1, ya descifrado, para contrastarlo con el DNI.
 *
 * <p>No es {@link IdentidadDeclarada}: aquella valida la entrada de un formulario (mayoria
 * de edad respecto a hoy, longitudes), y estos datos ya se validaron al registrarse. Volver
 * a pasarlos por esas reglas podria rechazar a alguien por un motivo que no tiene nada que
 * ver con la comparacion.
 */
public record DatosDeclarados(String nombres, String apellidos, TipoDocumento tipoDocumento,
                              String numeroDocumento, LocalDate fechaNacimiento) {

    @Override
    public String toString() {
        return "DatosDeclarados[tipo=" + tipoDocumento + ", resto=oculto]";
    }
}
