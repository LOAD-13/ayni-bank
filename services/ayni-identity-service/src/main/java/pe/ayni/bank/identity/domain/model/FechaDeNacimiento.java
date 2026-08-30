package pe.ayni.bank.identity.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Fecha de nacimiento declarada, con la mayoria de edad ya comprobada.
 *
 * <p>No es un record porque la validacion necesita saber que dia es hoy, y un record
 * expone siempre su constructor canonico: cualquiera podria construir uno saltandose la
 * comprobacion. Con constructor privado y fabrica, la unica forma de tener una
 * {@code FechaDeNacimiento} es haberla validado, que es el mismo criterio que sigue
 * {@link Usuario}.
 *
 * <p>Los dieciocho anos no son una preferencia de producto: en el Peru una persona no
 * puede celebrar por si misma un contrato bancario antes de esa edad.
 */
public final class FechaDeNacimiento {

    /** Edad minima para abrir una cuenta a nombre propio. */
    public static final int EDAD_MINIMA = 18;

    /**
     * Tope superior. No pretende ser un limite biologico exacto, sino atrapar el error de
     * tecleo —un ano de 1900 en vez de 1990— antes de que llegue a la base de datos.
     */
    private static final int EDAD_MAXIMA = 120;

    private final LocalDate valor;

    private FechaDeNacimiento(LocalDate valor) {
        this.valor = valor;
    }

    /**
     * @param hoy el dia de referencia. Se recibe en lugar de leer el reloj del sistema
     *            para que la regla sea comprobable sin depender de cuando se ejecute la
     *            prueba, y para que el servicio use el mismo {@code Clock} que ya inyecta.
     */
    public static FechaDeNacimiento de(LocalDate valor, LocalDate hoy) {
        // IllegalArgumentException y no NullPointerException: el manejador de errores
        // traduce la primera a un 400 con su mensaje, y la segunda a un 500 generico. Un
        // campo que falta es culpa de la peticion, no del servidor.
        if (valor == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }
        Objects.requireNonNull(hoy, "Falta la fecha de referencia.");

        if (valor.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha de nacimiento no puede ser futura.");
        }

        int edad = Period.between(valor, hoy).getYears();
        if (edad < EDAD_MINIMA) {
            throw new IllegalArgumentException(
                    "Debes tener al menos " + EDAD_MINIMA + " anos para abrir una cuenta.");
        }
        if (edad > EDAD_MAXIMA) {
            throw new IllegalArgumentException("Revisa la fecha de nacimiento.");
        }

        return new FechaDeNacimiento(valor);
    }

    public LocalDate valor() {
        return valor;
    }

    public int edadEn(LocalDate hoy) {
        return Period.between(valor, hoy).getYears();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof FechaDeNacimiento fecha && valor.equals(fecha.valor);
    }

    @Override
    public int hashCode() {
        return valor.hashCode();
    }

    /**
     * Sin la fecha. Combinada con el nombre identifica a una persona, y la Ley N.o 29733
     * la trata como dato personal: no tiene por que aparecer en una traza.
     */
    @Override
    public String toString() {
        return "FechaDeNacimiento[oculta]";
    }
}
