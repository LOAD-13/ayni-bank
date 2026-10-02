package pe.ayni.bank.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una foto aceptada de un lado del DNI: donde esta en el almacen y su huella.
 *
 * <p>En la base de datos solo se guarda esto: la referencia, el SHA-256, el tipo MIME y el
 * tamano (criterio de aceptacion de HU-02). La imagen nunca pasa por identity-service.
 */
public record DocumentoKyc(UUID id, UUID solicitudId, TipoDeDocumentoKyc tipo, String claveDeObjeto,
                           String hashSha256, String tipoDeContenido, long tamanoBytes, Instant subidoEn) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public DocumentoKyc {
        Objects.requireNonNull(id, "Falta el identificador.");
        Objects.requireNonNull(solicitudId, "Falta la solicitud.");
        Objects.requireNonNull(tipo, "Falta el tipo de documento.");
        Objects.requireNonNull(claveDeObjeto, "Falta la clave del objeto.");
        Objects.requireNonNull(tipoDeContenido, "Falta el tipo de contenido.");
        Objects.requireNonNull(subidoEn, "Falta el momento de la subida.");
        if (hashSha256 == null || !SHA256_HEX.matcher(hashSha256).matches()) {
            throw new IllegalArgumentException("El hash debe ser SHA-256 en hexadecimal minuscula.");
        }
        if (tamanoBytes <= 0) {
            throw new IllegalArgumentException("El tamano del documento debe ser positivo.");
        }
    }
}
