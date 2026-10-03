package pe.ayni.bank.identity.infrastructure.out.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.port.out.CifradorDeDatosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;

/** Implementa {@link RepositorioDeDocumentosKycPort} sobre JPA. */
@Repository
public class AdaptadorRepositorioDeDocumentosKyc implements RepositorioDeDocumentosKycPort {

    /** Las lecturas de la maquina; las del titular no cuentan como lectura del OCR. */
    private static final List<String> FUENTES_DEL_OCR =
            List.of(FuenteDeLectura.MRZ.name(), FuenteDeLectura.HEURISTICA_ANVERSO.name());

    private final DocumentoKycJpaRepository documentos;
    private final LecturaDniJpaRepository lecturas;
    private final CifradorDeDatosPort cifrador;
    private final Clock reloj;

    AdaptadorRepositorioDeDocumentosKyc(DocumentoKycJpaRepository documentos,
                                        LecturaDniJpaRepository lecturas,
                                        CifradorDeDatosPort cifrador,
                                        Clock reloj) {
        this.documentos = documentos;
        this.lecturas = lecturas;
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void guardar(DocumentoKyc documento) {
        documentos.save(new DocumentoKycEntity(
                documento.id(), documento.solicitudId(), documento.tipo().name(), documento.claveDeObjeto(),
                documento.hashSha256(), documento.tipoDeContenido(), documento.tamanoBytes(),
                documento.subidoEn()));
    }

    @Override
    public Optional<DocumentoKyc> ultimoDe(UUID solicitudId, TipoDeDocumentoKyc tipo) {
        return documentos.findFirstBySolicitudIdAndTipoDocumentoOrderBySubidoEnDesc(solicitudId, tipo.name())
                .map(fila -> new DocumentoKyc(
                        fila.getId(), fila.getSolicitudId(), TipoDeDocumentoKyc.valueOf(fila.getTipoDocumento()),
                        fila.getObjectKey(), fila.getHashSha256(), fila.getMimeType(), fila.getTamanoBytes(),
                        fila.getSubidoEn()));
    }

    /** El numero se cifra aqui, igual que el declarado en {@code AdaptadorRepositorioDeSolicitudes}. */
    @Override
    @Transactional
    public void guardarLectura(UUID solicitudId, LecturaDelDni lectura) {
        DatosDelDni datos = lectura.datos();
        lecturas.save(new LecturaDniEntity(
                UUID.randomUUID(), solicitudId, lectura.fuente().name(), lectura.confiable(),
                cifrador.cifrar(datos.numero()), datos.ultimos4(), datos.nombres(), datos.apellidos(),
                datos.fechaNacimiento(), datos.sexo(), datos.fechaEmision(), reloj.instant()));
    }

    @Override
    public Optional<LecturaDelDni> ultimaLecturaOcrDe(UUID solicitudId) {
        return lecturas.findFirstBySolicitudIdAndFuenteInOrderByLeidaEnDesc(solicitudId, FUENTES_DEL_OCR)
                .map(fila -> new LecturaDelDni(
                        new DatosDelDni(cifrador.descifrar(fila.getNumeroCifrado()), fila.getNombres(),
                                fila.getApellidos(), fila.getFechaNacimiento(), fila.getSexo(),
                                fila.getFechaEmision()),
                        FuenteDeLectura.valueOf(fila.getFuente()),
                        fila.isConfiable()));
    }
}
