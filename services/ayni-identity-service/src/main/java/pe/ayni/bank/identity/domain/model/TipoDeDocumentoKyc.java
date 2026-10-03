package pe.ayni.bank.identity.domain.model;

import java.util.Locale;

/**
 * Que cara/imagen del proceso de verificacion se esta subiendo.
 *
 * <p>No confundir con {@link TipoDocumento} (DNI/CE/Pasaporte, el documento de
 * identidad <em>declarado</em> en HU-01). Este enum es distinto: representa el
 * archivo concreto que sube el navegador durante HU-02.
 */
public enum TipoDeDocumentoKyc {

    ANVERSO,
    REVERSO,
    SELFIE;

    /**
     * Tamano maximo de cada foto. Lo impone la politica de subida del almacen, no solo el
     * navegador (ver {@link UrlDeSubida}).
     */
    public static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private static final String IMAGEN_JPEG = "image/jpeg";

    /**
     * Sin PDF: kyc-service analiza la imagen con OpenCV, que no lee PDF, y un PDF subido
     * se rechazaria siempre como "no es un DNI" sin que el solicitante supiera por que.
     */
    public String tipoDeContenidoEsperado(String extension) {
        if ("png".equalsIgnoreCase(extension)) {
            return "image/png";
        }
        if ("webp".equalsIgnoreCase(extension)) {
            return "image/webp";
        }
        return IMAGEN_JPEG;
    }

    /** Prefijo del nombre del objeto en el almacen: {@code anverso-}, {@code reverso-}... */
    public String prefijoDeObjeto() {
        return name().toLowerCase(Locale.ROOT) + "-";
    }
}
