package pe.ayni.bank.identity.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * La operacion que el titular confirma con su segundo factor (HU-07). Ver ADR-0031.
 *
 * <p>identity no mueve dinero ni sabe de cuentas: solo calcula la huella de lo que el
 * titular dice que va a hacer y la firma dentro del token. core-banking calcula la misma
 * huella con lo que llega en la transferencia. La forma canonica es un contrato entre los
 * dos servicios y las dos suites de pruebas comparten un vector de referencia:
 * {@code TRANSFERENCIA|<destino sin espacios ni guiones>|<importe con dos decimales>|<moneda>|<clave>}.
 */
public record OperacionAConfirmar(String destino, BigDecimal importe, String moneda, UUID clave) {

    public OperacionAConfirmar {
        Objects.requireNonNull(destino, "El destino es obligatorio.");
        Objects.requireNonNull(importe, "El importe es obligatorio.");
        Objects.requireNonNull(moneda, "La moneda es obligatoria.");
        Objects.requireNonNull(clave, "La clave de idempotencia es obligatoria.");
        if (importe.signum() <= 0) {
            throw new IllegalArgumentException("El importe debe ser positivo.");
        }
        if (!moneda.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("La moneda debe ser un codigo ISO 4217.");
        }
    }

    public String canonica() {
        return String.join("|", "TRANSFERENCIA",
                destino.replaceAll("[\s-]", ""),
                importe.setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                moneda,
                clave.toString());
    }

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
