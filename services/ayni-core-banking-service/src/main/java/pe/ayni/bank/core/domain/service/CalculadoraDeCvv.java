package pe.ayni.bank.core.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import pe.ayni.bank.core.domain.model.Pan;

/**
 * CVV dinamico · HU-09: tres digitos que cambian cada {@link #VENTANA} y no se guardan nunca.
 *
 * <p>Es {@code HMAC-SHA256(clave, PAN | ventana)} reducido a tres digitos, con la misma idea
 * que un codigo TOTP. Como depende de una clave que solo tiene core-banking, quien robe la
 * base de datos no puede calcularlo, y quien vea un CVV solo le sirve hasta que cambie la
 * ventana. Guardarlo esta prohibido por PCI DSS, y asi no hay nada que guardar.
 */
public class CalculadoraDeCvv {

    public static final Duration VENTANA = Duration.ofMinutes(5);

    private final SecretKeySpec clave;

    public CalculadoraDeCvv(byte[] clave) {
        if (clave == null || clave.length < 32) {
            throw new IllegalArgumentException("La clave del CVV debe tener al menos 32 bytes.");
        }
        this.clave = new SecretKeySpec(clave, "HmacSHA256");
    }

    public String calcular(Pan pan, Instant ahora) {
        long ventana = ahora.getEpochSecond() / VENTANA.toSeconds();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            byte[] resumen = mac.doFinal((pan.valor() + "|" + ventana).getBytes(StandardCharsets.UTF_8));
            int numero = ((resumen[0] & 0x7f) << 24) | ((resumen[1] & 0xff) << 16)
                    | ((resumen[2] & 0xff) << 8) | (resumen[3] & 0xff);
            return "%03d".formatted(numero % 1000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible en esta JVM.", e);
        }
    }

    /** Hasta cuando vale el CVV que se calcule en {@code ahora}: el fin de su ventana. */
    public Instant validoHasta(Instant ahora) {
        long ventana = ahora.getEpochSecond() / VENTANA.toSeconds();
        return Instant.ofEpochSecond((ventana + 1) * VENTANA.toSeconds());
    }
}
