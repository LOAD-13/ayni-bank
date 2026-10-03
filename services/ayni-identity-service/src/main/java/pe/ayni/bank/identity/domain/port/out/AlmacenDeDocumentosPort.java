package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;

import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;

/**
 * Genera acceso temporal y directo al almacen de objetos de los documentos KYC.
 *
 * <p>El dominio no conoce MinIO, S3 ni ningun SDK: solo pide una URL de subida
 * para una clave de objeto y un tipo de contenido. Ver diseno-base.md §4.1.
 */
public interface AlmacenDeDocumentosPort {

    /** Politica de subida que solo admite ese tipo de contenido y hasta ese tamano. */
    UrlDeSubida generarUrlDeSubida(String claveDeObjeto, String tipoDeContenido, long tamanoMaximoBytes);

    /**
     * SHA-256 del objeto ya subido, en hexadecimal minuscula. Se guarda en
     * {@code documento_kyc.hash_sha256} y se vuelve a calcular antes de leer los datos,
     * para detectar que el objeto se altero despues de evaluarlo (diseno-base.md §4.1).
     */
    String calcularHash(String claveDeObjeto);

    /** Tamano y tipo del objeto, sin descargarlo; vacio si la clave no existe. */
    Optional<ObjetoAlmacenado> describir(String claveDeObjeto);

    /**
     * Borra el objeto. Una foto rechazada no se conserva (HU-02, escenario 2): no tiene
     * finalidad y es dato personal (Ley N.o 29733).
     */
    void eliminar(String claveDeObjeto);
}
