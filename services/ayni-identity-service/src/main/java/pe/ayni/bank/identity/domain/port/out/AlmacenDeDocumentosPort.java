package pe.ayni.bank.identity.domain.port.out;

import pe.ayni.bank.identity.domain.model.UrlDeSubida;

/**
 * Genera acceso temporal y directo al almacen de objetos de los documentos KYC.
 *
 * <p>El dominio no conoce MinIO, S3 ni ningun SDK: solo pide una URL de subida
 * para una clave de objeto y un tipo de contenido. Ver diseno-base.md §4.1.
 */
public interface AlmacenDeDocumentosPort {

    UrlDeSubida generarUrlDeSubida(String claveDeObjeto, String tipoDeContenido);

    /**
     * SHA-256 del objeto ya subido, en hexadecimal minuscula. Permite
     * detectar alteracion del objeto (diseno-base.md §4.1). No persiste en
     * base de datos todavia: eso llega con la tabla {@code documento_kyc}
     * (AYNI-13 subtarea 9).
     */
    String calcularHash(String claveDeObjeto);
}
