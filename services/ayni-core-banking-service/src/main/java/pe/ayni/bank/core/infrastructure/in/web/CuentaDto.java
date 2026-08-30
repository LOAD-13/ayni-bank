package pe.ayni.bank.core.infrastructure.in.web;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;

/**
 * La cuenta tal como la ve la pantalla final del onboarding.
 *
 * <p>El saldo viaja como cadena y no como numero. Es deliberado: JSON no distingue enteros
 * de decimales y JavaScript representa todo con coma flotante de doble precision, con lo
 * que 12480.65 puede llegar al navegador como 12480.649999999999. En texto llega exacto y
 * la interfaz lo formatea sin haber tocado nunca el valor.
 */
public record CuentaDto(String cuentaId, String numero, String numeroFormateado, String cci,
                        String cciFormateado, String moneda, String estado, String saldo,
                        String trea, String comisionDeMantenimiento) {

    /** Ayni no cobra mantenimiento. Es el argumento del producto y por eso viaja explicito. */
    private static final String SIN_COMISION = "0.00";

    public static CuentaDto desde(Cuenta cuenta, Dinero saldo, java.math.BigDecimal trea) {
        return new CuentaDto(
                cuenta.id().toString(),
                cuenta.numero().valor(),
                cuenta.numero().formateado(),
                cuenta.cci().valor(),
                cuenta.cci().formateado(),
                cuenta.moneda().name(),
                cuenta.estado().name(),
                saldo.importe().toPlainString(),
                comoPorcentaje(trea),
                SIN_COMISION);
    }

    /**
     * La tasa del catalogo esta guardada como fraccion —0.045000— y la pantalla la muestra
     * como porcentaje —4.50 %—.
     *
     * <p>La conversion vive aqui y no en la base ni en el dominio a proposito. Guardar
     * fracciones es lo correcto: es la forma en la que se usa la tasa para calcular, y
     * guardarla ya multiplicada obligaria a dividir en cada devengo, que es una operacion
     * mas donde equivocarse. Que se enseñe en porcentaje es una decision de presentacion, y
     * este es el borde donde se presenta.
     */
    private static String comoPorcentaje(java.math.BigDecimal fraccion) {
        if (fraccion == null) {
            return null;
        }
        return fraccion.movePointRight(2)
                .setScale(2, java.math.RoundingMode.HALF_EVEN)
                .toPlainString();
    }
}
