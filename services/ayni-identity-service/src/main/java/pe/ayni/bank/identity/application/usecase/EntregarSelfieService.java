package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.DocumentoNoSubidoException;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.port.in.EntregarSelfieUseCase;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * Registra la selfie y deja la solicitud en manos de un operador para el cotejo facial.
 *
 * <p>Antes, la web subia la selfie y nada mas: ningun servicio la miraba ni aprobaba la
 * solicitud, asi que en produccion ninguna cuenta podia abrirse. Mientras el cotejo
 * automatico con kyc-service no este integrado, la decision la toma una persona; la
 * pantalla ya le promete al solicitante exactamente eso.
 *
 * <p>Las mismas garantias que la foto del DNI: la clave tiene que ser de esta solicitud y
 * de una selfie, el objeto tiene que existir y su hash queda registrado.
 */
@Service
public class EntregarSelfieService implements EntregarSelfieUseCase {

    private static final String TIPO_DESCONOCIDO = "application/octet-stream";

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeDocumentosKycPort documentos;
    private final AlmacenDeDocumentosPort almacen;
    private final GestionarFalloDeVerificacionKycUseCase fallos;
    private final Clock reloj;

    public EntregarSelfieService(RepositorioDeSolicitudesPort solicitudes,
                                 RepositorioDeDocumentosKycPort documentos,
                                 AlmacenDeDocumentosPort almacen,
                                 GestionarFalloDeVerificacionKycUseCase fallos,
                                 Clock reloj) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.almacen = almacen;
        this.fallos = fallos;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public EstadoDelPasoKyc entregar(UUID solicitudId, String claveDeObjeto) {
        solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoExisteException(
                        "La solicitud no existe o es un senuelo sin titular."));

        String prefijo = "kyc/" + solicitudId + "/" + TipoDeDocumentoKyc.SELFIE.prefijoDeObjeto();
        if (claveDeObjeto == null || !claveDeObjeto.startsWith(prefijo)) {
            throw new DocumentoNoSubidoException("La foto no corresponde a la selfie de esta solicitud.");
        }

        ObjetoAlmacenado objeto = almacen.describir(claveDeObjeto)
                .orElseThrow(() -> new DocumentoNoSubidoException("La selfie no se ha subido o ya no esta disponible."));
        if (objeto.tamanoBytes() > TipoDeDocumentoKyc.TAMANO_MAXIMO_BYTES) {
            almacen.eliminar(claveDeObjeto);
            throw new DocumentoNoSubidoException("La foto supera el tamano maximo de 5 MB.");
        }

        documentos.guardar(new DocumentoKyc(
                UUID.randomUUID(), solicitudId, TipoDeDocumentoKyc.SELFIE, claveDeObjeto,
                almacen.calcularHash(claveDeObjeto),
                Objects.requireNonNullElse(objeto.tipoDeContenido(), TIPO_DESCONOCIDO),
                objeto.tamanoBytes(), reloj.instant()));

        fallos.derivarParaCotejoFacial(solicitudId);
        return EstadoDelPasoKyc.EN_REVISION_MANUAL;
    }
}
