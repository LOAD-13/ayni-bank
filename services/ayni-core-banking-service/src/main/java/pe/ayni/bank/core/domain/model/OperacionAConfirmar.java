package pe.ayni.bank.core.domain.model;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * La transferencia tal como la confirmo el titular con su segundo factor (HU-07). Ver ADR-0031.
 *
 * <p>Su huella va dentro del token de confirmacion que emite identity. Core la vuelve a
 * calcular con lo que llega en la peticion: si alguien cambia el destino, el importe o la
 * clave despues de confirmar, la huella deja de coincidir y la transferencia no se hace.
 *
 * <p>La forma canonica la calculan dos servicios por separado, asi que es un contrato:
 * {@code TRANSFERENCIA|<destino sin espacios ni guiones>|<importe con dos decimales>|<moneda>|<clave>}.
 */
public record OperacionAConfirmar(String destino, Dinero importe, UUID clave) {

    public OperacionAConfirmar {
        Objects.requireNonNull(destino);
        Objects.requireNonNull(importe);
        Objects.requireNonNull(clave);
    }

    public String canonica() {
        return String.join("|", "TRANSFERENCIA",
                destino.replaceAll("[\\s-]", ""),
                importe.importe().setScale(Dinero.DECIMALES, RoundingMode.HALF_EVEN).toPlainString(),
                importe.moneda().name(),
                clave.toString());
    }

    /** SHA-256 de la forma canonica, en Base64. */
    public String huella() {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(canonica().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
    }
}
