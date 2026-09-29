package pe.ayni.bank.identity.domain.model;

/**
 * Que cara/imagen del proceso de verificacion se esta subiendo.
 *
 * <p>No confundir con {@link TipoDocumento} (DNI/CE/Pasaporte, el documento de
 * identidad <em>declarado</em> en HU-01). Este enum es distinto: representa el
 * archivo concreto que sube el navegador durante HU-02.
 */
public enum TipoDeDocumentoKyc {

    ANVERSO("image/jpeg"),
    REVERSO("image/jpeg"),
    SELFIE("image/jpeg");

    private final String tipoDeContenidoEsperado;

    TipoDeDocumentoKyc(String tipoDeContenidoEsperado) {
        this.tipoDeContenidoEsperado = tipoDeContenidoEsperado;
    }

    public String tipoDeContenidoEsperado(String extension) {
        if ("pdf".equalsIgnoreCase(extension)) {
            return "application/pdf";
        }
        if ("png".equalsIgnoreCase(extension)) {
            return "image/png";
        }
        if ("webp".equalsIgnoreCase(extension)) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
