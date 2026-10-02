package pe.ayni.bank.core.domain.model;

/**
 * Codigo de Cuenta Interbancario: los veinte digitos con los que otro banco puede
 * transferirle dinero a esta cuenta.
 *
 * <pre>
 *   999  001  123456789012  47
 *   ───  ───  ────────────  ──
 *   banco oficina  cuenta   control
 * </pre>
 *
 * <p><strong>Los dos digitos de control no son adorno.</strong> Un CCI se teclea a mano, y
 * un digito mal copiado manda el dinero a otra persona. Con la comprobacion, casi cualquier
 * error de tecleo —un digito cambiado, dos digitos intercambiados— se detecta antes de
 * mover nada.
 *
 * <p><strong>Aviso sobre el algoritmo.</strong> La estructura de veinte digitos es la
 * correcta. El calculo concreto de los digitos de control que se implementa aqui es una
 * comprobacion ponderada estandar, <em>no</em> necesariamente el que publica la SBS: esa
 * especificacion no es de acceso abierto. Sirve para que el sistema sea coherente consigo
 * mismo, que es lo que necesita el Sprint 1, pero <strong>antes de conectar con la camara
 * de compensacion hay que contrastarlo con la especificacion oficial</strong>, y si difiere
 * se cambia solo este fichero.
 */
public record Cci(String valor) {

    /**
     * Codigo de entidad de Ayni Bank. Es provisional: lo asigna la SBS al autorizar la
     * entidad, y hasta entonces cualquier valor que se elija es un marcador.
     */
    public static final String CODIGO_DE_BANCO = "999";

    /** Oficina unica: Ayni no tiene agencias. */
    public static final String CODIGO_DE_OFICINA = "001";

    private static final int LONGITUD = 20;
    private static final int LONGITUD_DE_CUENTA = 12;

    /**
     * Pesos de la comprobacion, aplicados de derecha a izquierda sobre los dieciocho
     * primeros digitos.
     */
    private static final int[] PESOS = {3, 2, 9, 8, 7, 6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    public Cci {
        if (valor == null || !valor.matches("^[0-9]{" + LONGITUD + "}$")) {
            throw new IllegalArgumentException("El CCI debe tener " + LONGITUD + " digitos.");
        }
        if (!controlCorrecto(valor)) {
            throw new IllegalArgumentException("El CCI no supera la comprobacion de control.");
        }
    }

    /**
     * Construye el CCI de una cuenta, calculando sus digitos de control.
     *
     * <p>Del numero de cuenta se descarta el prefijo de oficina, que en el CCI viaja en su
     * propio campo: repetirlo dejaria la oficina dos veces y solo once digitos utiles para
     * identificar la cuenta.
     */
    public static Cci para(NumeroDeCuenta numero) {
        String sinLaOficina = numero.valor().substring(CODIGO_DE_OFICINA.length());
        String cuerpo = CODIGO_DE_BANCO + CODIGO_DE_OFICINA + rellenar(sinLaOficina);
        return new Cci(cuerpo + control(cuerpo));
    }

    public String banco() {
        return valor.substring(0, 3);
    }

    public String oficina() {
        return valor.substring(3, 6);
    }

    /** Los ultimos cuatro digitos, que es lo unico que se muestra en listados. */
    public String ultimos4() {
        return valor.substring(valor.length() - 4);
    }

    /** Agrupado de tres en tres para que se pueda leer en voz alta sin perderse. */
    public String formateado() {
        return valor.replaceAll("(.{3})(.{3})(.{4})(.{4})(.{4})(.{2})", "$1-$2-$3-$4-$5-$6");
    }

    private static String rellenar(String numero) {
        if (numero.length() > LONGITUD_DE_CUENTA) {
            throw new IllegalArgumentException(
                    "El numero de cuenta no cabe en los " + LONGITUD_DE_CUENTA
                            + " digitos que el CCI reserva.");
        }
        return "0".repeat(LONGITUD_DE_CUENTA - numero.length()) + numero;
    }

    private static boolean controlCorrecto(String candidato) {
        String cuerpo = candidato.substring(0, LONGITUD - 2);
        return control(cuerpo).equals(candidato.substring(LONGITUD - 2));
    }

    private static String control(String cuerpo) {
        int suma = 0;
        for (int i = 0; i < cuerpo.length(); i++) {
            // De derecha a izquierda: el peso de cada posicion depende de su distancia al
            // final, no de su indice, para que anadir digitos por delante no desplace todo.
            int peso = PESOS[(cuerpo.length() - 1 - i) % PESOS.length];
            suma += Character.getNumericValue(cuerpo.charAt(i)) * peso;
        }
        return String.format("%02d", suma % 97);
    }

    @Override
    public String toString() {
        return formateado();
    }
}
