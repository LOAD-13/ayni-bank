package pe.ayni.bank.notification.domain.model;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Un intento de envio, salga bien o mal. Ninguno se borra. */
public record IntentoDeEnvio(UUID id, UUID notificacionId, int numero, boolean enviado, String detalle,
                             Instant ocurridoEn) {

    private static final int LARGO_MAXIMO = 500;
    private static final Pattern CORREO = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");

    public IntentoDeEnvio {
        detalle = detalle == null ? null : sinDatosPersonales(detalle);
    }

    /**
     * El detalle viene del proveedor de correo, y SES incluye la direccion en algunos
     * errores ("the following identities failed the check: ..."). Se tacha antes de guardar.
     */
    static String sinDatosPersonales(String detalle) {
        String limpio = CORREO.matcher(detalle).replaceAll("[correo]");
        return limpio.length() <= LARGO_MAXIMO ? limpio : limpio.substring(0, LARGO_MAXIMO);
    }
}
