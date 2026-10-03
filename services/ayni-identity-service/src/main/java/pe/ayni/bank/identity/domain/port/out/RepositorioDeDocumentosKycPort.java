package pe.ayni.bank.identity.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;

/**
 * Fotos aceptadas del DNI y lecturas de sus datos.
 *
 * <p>Las lecturas se guardan todas, las del OCR y las que confirma el titular, cada una
 * con su fuente. Asi siempre se puede responder a «¿este dato lo leyo una maquina o lo
 * escribio la persona?» (ADR-0009).
 */
public interface RepositorioDeDocumentosKycPort {

    void guardar(DocumentoKyc documento);

    /** La ultima foto aceptada de ese lado, que es la que vale si se repitio. */
    Optional<DocumentoKyc> ultimoDe(UUID solicitudId, TipoDeDocumentoKyc tipo);

    void guardarLectura(UUID solicitudId, LecturaDelDni lectura);

    /** La ultima lectura del OCR (MRZ o anverso), sin contar las del titular. */
    Optional<LecturaDelDni> ultimaLecturaOcrDe(UUID solicitudId);
}
