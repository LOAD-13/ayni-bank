package pe.ayni.bank.identity.domain.model;

import java.time.LocalDate;

/**
 * Lo que el titular envia al confirmar la lectura de su DNI.
 *
 * <p>El numero puede venir vacio: la pantalla lo muestra enmascarado y solo lo pide si el
 * titular dice que esta mal. Vacio significa «el que se leyo es correcto».
 */
public record DatosConfirmados(String numero, String nombres, String apellidos,
                               LocalDate fechaNacimiento, String sexo, LocalDate fechaEmision) {

    /** Los datos completos, tomando de la lectura lo que el titular no reescribio. */
    public DatosDelDni completarCon(DatosDelDni leidos) {
        String numeroFinal = numero == null || numero.isBlank() ? leidos.numero() : numero;
        return new DatosDelDni(numeroFinal, nombres, apellidos, fechaNacimiento, sexo, fechaEmision);
    }

    @Override
    public String toString() {
        return "DatosConfirmados[oculto]";
    }
}
