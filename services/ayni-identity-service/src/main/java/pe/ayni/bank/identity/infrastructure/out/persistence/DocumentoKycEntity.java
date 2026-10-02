package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de {@code identity.documento_kyc} (V5). Solo la referencia al objeto, nunca la imagen. */
@Entity
@Table(name = "documento_kyc")
class DocumentoKycEntity {

    @Id
    private UUID id;

    @Column(name = "solicitud_id", nullable = false)
    private UUID solicitudId;

    @Column(name = "tipo_documento", nullable = false, length = 16)
    private String tipoDocumento;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(name = "subido_en", nullable = false)
    private Instant subidoEn;

    protected DocumentoKycEntity() {
        // Exigido por JPA.
    }

    DocumentoKycEntity(UUID id, UUID solicitudId, String tipoDocumento, String objectKey,
                       String hashSha256, String mimeType, long tamanoBytes, Instant subidoEn) {
        this.id = id;
        this.solicitudId = solicitudId;
        this.tipoDocumento = tipoDocumento;
        this.objectKey = objectKey;
        this.hashSha256 = hashSha256;
        this.mimeType = mimeType;
        this.tamanoBytes = tamanoBytes;
        this.subidoEn = subidoEn;
    }

    UUID getId() {
        return id;
    }

    UUID getSolicitudId() {
        return solicitudId;
    }

    String getTipoDocumento() {
        return tipoDocumento;
    }

    String getObjectKey() {
        return objectKey;
    }

    String getHashSha256() {
        return hashSha256;
    }

    String getMimeType() {
        return mimeType;
    }

    long getTamanoBytes() {
        return tamanoBytes;
    }

    Instant getSubidoEn() {
        return subidoEn;
    }
}
