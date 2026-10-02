package pe.ayni.bank.core.domain.model;

/**
 * Numero de cuenta interno, de catorce digitos.
 *
 * <pre>
 *   001  1  9876543210
 *   ───  ─  ──────────
 *   ofic mon  correlativo
 * </pre>
 *
 * <p>Es el numero corto que ve el cliente en su aplicacion. Para recibir dinero de otro
 * banco hace falta el {@link Cci}, que lo contiene.
 *
 * <p>Lleva la moneda dentro porque una misma persona puede tener cuenta en soles y en
 * dolares, y confundirlas al hacer una transferencia es un error caro. Con la moneda a la
 * vista en el propio numero, el error se ve antes de confirmarlo.
 */
public record NumeroDeCuenta(String valor) {

    private static final int LONGITUD = 14;

    public NumeroDeCuenta {
        if (valor == null || !valor.matches("^[0-9]{" + LONGITUD + "}$")) {
            throw new IllegalArgumentException(
                    "El numero de cuenta debe tener " + LONGITUD + " digitos.");
        }
    }

    /**
     * @param correlativo valor unico y creciente que entrega la base de datos. Se recibe en
     *                    lugar de generarlo aqui porque garantizar unicidad es cosa de la
     *                    base: dos peticiones simultaneas que generen el numero en memoria
     *                    pueden coincidir, y el indice unico las rechazaria despues de
     *                    haber hecho ya todo el trabajo.
     */
    public static NumeroDeCuenta de(Moneda moneda, long correlativo) {
        if (correlativo < 0 || correlativo > 9_999_999_999L) {
            throw new IllegalArgumentException("El correlativo de cuenta se ha agotado.");
        }
        return new NumeroDeCuenta(
                Cci.CODIGO_DE_OFICINA + digitoDe(moneda) + String.format("%010d", correlativo));
    }

    public Moneda moneda() {
        return valor.charAt(3) == '1' ? Moneda.PEN : Moneda.USD;
    }

    /**
     * Agrupado como lo muestra el diseno: {@code 001-1987654-3-210}.
     *
     * <p>Catorce digitos seguidos no los lee nadie sin perder la cuenta. El agrupado no es
     * decoracion: es lo que hace que alguien pueda dictarlo por telefono o compararlo con
     * un papel sin equivocarse.
     */
    public String formateado() {
        return valor.replaceAll("(.{3})(.{7})(.)(.{3})", "$1-$2-$3-$4");
    }

    /** Lo que se muestra en un listado: {@code ****3210}. */
    public String enmascarado() {
        return "*".repeat(LONGITUD - 4) + valor.substring(LONGITUD - 4);
    }

    private static char digitoDe(Moneda moneda) {
        return moneda == Moneda.PEN ? '1' : '2';
    }

    @Override
    public String toString() {
        return valor;
    }
}
