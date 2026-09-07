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
}
