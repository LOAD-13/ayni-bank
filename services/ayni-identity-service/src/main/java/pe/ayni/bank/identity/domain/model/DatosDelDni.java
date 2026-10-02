package pe.ayni.bank.identity.domain.model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Los datos impresos en un DNI peruano, leidos por OCR o confirmados por su titular.
 *
 * <p>El numero son exactamente ocho digitos (criterio de aceptacion de HU-02). La fecha de
 * emision puede faltar: el MRZ no la trae y el OCR del anverso no siempre la encuentra.
 *
 * <p>Todo es dato personal (Ley N.o 29733): {@link #toString()} no devuelve ninguno.
 */
public record DatosDelDni(String numero, String nombres, String apellidos,
                          LocalDate fechaNacimiento, String sexo, LocalDate fechaEmision) {

    private static final Pattern OCHO_DIGITOS = Pattern.compile("^\\d{8}$");
    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");

    /** Los mismos topes que {@link IdentidadDeclarada} y que la tabla {@code lectura_dni}. */
    private static final int MAXIMO_NOMBRES = 80;
    private static final int MAXIMO_APELLIDOS = 120;

    public DatosDelDni {
        if (numero == null || !OCHO_DIGITOS.matcher(numero.trim()).matches()) {
            throw new IllegalArgumentException("El numero de DNI debe tener ocho digitos.");
        }
        numero = numero.trim();
        nombres = obligatorio(nombres, "nombres", MAXIMO_NOMBRES);
        apellidos = obligatorio(apellidos, "apellidos", MAXIMO_APELLIDOS);
        if (fechaNacimiento == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria.");
        }
        sexo = sexo == null ? "" : sexo.trim().toUpperCase(Locale.ROOT);
        if (!sexo.equals("M") && !sexo.equals("F")) {
            throw new IllegalArgumentException("El sexo debe ser M o F.");
        }
        if (fechaEmision != null && fechaEmision.isBefore(fechaNacimiento)) {
            throw new IllegalArgumentException("La fecha de emision no puede ser anterior al nacimiento.");
        }
    }

    /**
     * Si estos datos corresponden a lo que la persona declaro en el paso 1 (ADR-0009).
     *
     * <p>Los nombres se comparan sin tildes, sin distinguir mayusculas y con los espacios
     * colapsados: el DNI imprime «QUISPE MAMANI» y la persona escribe «Quispe Mamani», y
     * eso no es una discrepancia de identidad.
     */
    public boolean coincideCon(DatosDeclarados declarados) {
        Objects.requireNonNull(declarados, "Faltan los datos declarados.");
        return declarados.tipoDocumento() == TipoDocumento.DNI
                && numero.equals(declarados.numeroDocumento())
                && fechaNacimiento.equals(declarados.fechaNacimiento())
                && normalizar(nombres).equals(normalizar(declarados.nombres()))
                && normalizar(apellidos).equals(normalizar(declarados.apellidos()));
    }

    /**
     * Si la persona cambio algo que el MRZ ya habia comprobado con sus digitos verificadores.
     *
     * <p>El numero y la fecha de nacimiento de una lectura fiable no se equivocan por un
     * reflejo: si el titular los cambia, no esta corrigiendo al OCR, esta contradiciendo al
     * documento.
     */
    public boolean contradiceLoVerificadoEn(DatosDelDni leidos) {
        return !numero.equals(leidos.numero) || !fechaNacimiento.equals(leidos.fechaNacimiento);
    }

    /** Si es distinto de lo leido, sin contar mayusculas, tildes ni espacios. */
    public boolean difiereDe(DatosDelDni otros) {
        return !numero.equals(otros.numero)
                || !normalizar(nombres).equals(normalizar(otros.nombres))
                || !normalizar(apellidos).equals(normalizar(otros.apellidos))
                || !fechaNacimiento.equals(otros.fechaNacimiento)
                || !sexo.equals(otros.sexo)
                || !Objects.equals(fechaEmision, otros.fechaEmision);
    }

    public String ultimos4() {
        return numero.substring(4);
    }

    /** Apto para pantalla y para log: {@code ****5678}. */
    public String numeroEnmascarado() {
        return "****" + ultimos4();
    }

    private static String obligatorio(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El campo " + campo + " supera los " + maximo + " caracteres.");
        }
        return limpio;
    }

    static String normalizar(String texto) {
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
                .replaceAll("");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "DatosDelDni[numero=" + numeroEnmascarado() + ", resto=oculto]";
    }
}
