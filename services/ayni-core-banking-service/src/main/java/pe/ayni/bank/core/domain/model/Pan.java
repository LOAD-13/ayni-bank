package pe.ayni.bank.core.domain.model;

/**
 * El numero de la tarjeta (Primary Account Number), dieciseis digitos que superan la
 * comprobacion de Luhn · HU-09.
 *
 * <p><strong>Nunca se imprime entero por descuido.</strong> {@link #toString()} devuelve la
 * forma enmascarada, asi que un PAN que acaba en un log, en una excepcion o en un depurador
 * solo deja ver sus cuatro ultimos digitos. El numero completo solo sale por
 * {@link #formateado()}, que se llama a proposito al revelarlo con segundo factor.
 */
public record Pan(String valor) {

    public static final int LONGITUD = 16;

    public Pan {
        if (valor == null || !valor.matches("^[0-9]{" + LONGITUD + "}$")) {
            throw new IllegalArgumentException("El PAN debe tener " + LONGITUD + " digitos.");
        }
        if (!superaLuhn(valor)) {
            throw new IllegalArgumentException("El PAN no supera la comprobacion de Luhn.");
        }
    }

    /**
     * Comprobacion de Luhn (ISO/IEC 7812): de derecha a izquierda se duplica uno de cada dos
     * digitos, restando 9 si pasa de 9, y la suma total debe ser multiplo de 10. Detecta
     * cualquier error de un digito y casi todos los intercambios de dos contiguos.
     */
    public static boolean superaLuhn(String digitos) {
        int suma = 0;
        boolean duplicar = false;
        for (int i = digitos.length() - 1; i >= 0; i--) {
            int digito = digitos.charAt(i) - '0';
            if (duplicar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    /** El digito que hay que anadir a {@code sinControl} para que supere Luhn. */
    public static char digitoDeControl(String sinControl) {
        for (char candidato = '0'; candidato <= '9'; candidato++) {
            if (superaLuhn(sinControl + candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("Siempre hay un digito de control entre 0 y 9.");
    }

    public String ultimos4() {
        return valor.substring(LONGITUD - 4);
    }

    /** Lo que se muestra por defecto: {@code •••• •••• •••• 4821}. */
    public String enmascarado() {
        return "•••• •••• •••• " + ultimos4();
    }

    /** El numero entero en grupos de cuatro; solo para la revelacion con segundo factor. */
    public String formateado() {
        return valor.replaceAll("(.{4})(?!$)", "$1 ");
    }

    @Override
    public String toString() {
        return enmascarado();
    }
}
