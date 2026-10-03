package pe.ayni.bank.identity.domain.model;

/**
 * La clave recibida no es un documento subido para esta solicitud: no existe en el
 * almacen, pertenece a otra solicitud o a otro lado del DNI, o supera el tamano maximo.
 */
public class DocumentoNoSubidoException extends RuntimeException {

    public DocumentoNoSubidoException(String motivo) {
        super(motivo);
    }
}
