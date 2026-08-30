package pe.ayni.bank.identity.domain.model;

import java.util.Objects;

/**
 * Los datos de identidad tal como los escribio la persona en el paso 1 del onboarding.
 *
 * <p><strong>Por que se piden si el OCR va a leerlos igualmente.</strong> Precisamente por
 * eso. En HU-02 el servicio de KYC extrae del DNI el nombre, el numero y la fecha de
 * nacimiento; lo que se guarda aqui es el termino de comparacion. Un OCR que lee mal —una
 * foto borrosa, un reflejo sobre el plastico— produce datos plausibles pero equivocados, y
 * sin nada contra lo que contrastarlos nadie se entera. Con las dos lecturas, la
 * discrepancia salta y la solicitud va a revision manual en lugar de crear una identidad
 * falsa en silencio.
 *
 * <p>Va en la solicitud de onboarding y no en {@code persona}: {@code persona} guarda la
 * identidad verificada, y mezclar en la misma fila lo declarado con lo comprobado haria
 * imposible saber de donde vino cada dato.
 *
 * <p>Todo lo que contiene es dato personal. {@link #toString()} no devuelve ninguno.
 */
public record IdentidadDeclarada(String nombres, String apellidos,
                                 DocumentoDeIdentidad documento,
                                 FechaDeNacimiento fechaNacimiento) {

    /** Coincide con {@code persona.nombres} en la migracion V2. */
    private static final int MAXIMO_NOMBRES = 80;

    /** Los dos apellidos juntos, tal como los pide el formulario aprobado. */
    private static final int MAXIMO_APELLIDOS = 120;

    public IdentidadDeclarada {
        Objects.requireNonNull(documento, "El documento de identidad es obligatorio.");
        Objects.requireNonNull(fechaNacimiento, "La fecha de nacimiento es obligatoria.");

        nombres = normalizar(nombres, "nombres", MAXIMO_NOMBRES);
        apellidos = normalizar(apellidos, "apellidos", MAXIMO_APELLIDOS);
    }

    /**
     * Recorta los extremos y colapsa los espacios interiores. Sin esto, «Ana  Lucia» y
     * «Ana Lucia» son cadenas distintas, y la comparacion contra lo que lea el OCR fallaria
     * por un motivo que no tiene nada que ver con la identidad de nadie.
     */
    private static String normalizar(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " supera los " + maximo + " caracteres.");
        }
        return limpio;
    }

    @Override
    public String toString() {
        return "IdentidadDeclarada[nombres=ocultos, apellidos=ocultos, documento="
                + documento.enmascarado() + "]";
    }
}
