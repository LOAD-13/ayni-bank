package pe.ayni.bank.identity.application.usecase;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.DocumentoNoSubidoException;
import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.ResultadoDeCaptura;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.port.in.EvaluarCapturaDeDniUseCase;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.VerificadorKycPort;

/**
 * HU-02 · Evalua la foto de un lado del DNI (escenarios 1 a 5, ADR-0028).
 *
 * <p>Sin {@code @Transactional}: la llamada a kyc-service puede tardar hasta 10 s, y no hay
 * motivo para tener una transaccion de base de datos abierta mientras tanto. Cada escritura
 * es atomica por si sola.
 */
@Service
public class EvaluarCapturaDeDniService implements EvaluarCapturaDeDniUseCase {

    private static final String TIPO_DESCONOCIDO = "application/octet-stream";

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeDocumentosKycPort documentos;
    private final AlmacenDeDocumentosPort almacen;
    private final VerificadorKycPort verificador;
    private final GestionarFalloDeVerificacionKycUseCase fallos;
    private final Clock reloj;

    public EvaluarCapturaDeDniService(RepositorioDeSolicitudesPort solicitudes,
                                      RepositorioDeDocumentosKycPort documentos,
                                      AlmacenDeDocumentosPort almacen,
                                      VerificadorKycPort verificador,
                                      GestionarFalloDeVerificacionKycUseCase fallos,
                                      Clock reloj) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.almacen = almacen;
        this.verificador = verificador;
        this.fallos = fallos;
        this.reloj = reloj;
    }

    @Override
    public ResultadoDeCaptura evaluar(UUID solicitudId, TipoDeDocumentoKyc lado, String claveDeObjeto) {
        if (lado == TipoDeDocumentoKyc.SELFIE) {
            throw new IllegalArgumentException("Solo se evaluan el anverso y el reverso del DNI.");
        }
        solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoExisteException(
                        "La solicitud no existe o es un senuelo sin titular."));

        // La clave la firmo GenerarUrlDeSubidaService con este prefijo. Sin esta comprobacion,
        // cualquiera podria hacer pasar como suya la foto subida para otra solicitud.
        if (claveDeObjeto == null
                || !claveDeObjeto.startsWith("kyc/" + solicitudId + "/" + lado.prefijoDeObjeto())) {
            throw new DocumentoNoSubidoException("La foto no corresponde a esta solicitud o a este lado del DNI.");
        }

        if (solicitudes.estaEnRevisionManual(solicitudId)) {
            return ResultadoDeCaptura.enRevisionManual();
        }

        ObjetoAlmacenado objeto = almacen.describir(claveDeObjeto)
                .orElseThrow(() -> new DocumentoNoSubidoException("La foto no se ha subido o ya no esta disponible."));
        if (objeto.tamanoBytes() > TipoDeDocumentoKyc.TAMANO_MAXIMO_BYTES) {
            // La politica de subida ya lo impide; si llega aqui, alguien la esquivo.
            almacen.eliminar(claveDeObjeto);
            throw new DocumentoNoSubidoException("La foto supera el tamano maximo de 5 MB.");
        }

        // El hash se calcula ANTES de evaluar: lo que queda registrado es la huella de la
        // imagen que kyc-service vio, no de lo que haya en el almacen despues.
        String hash = almacen.calcularHash(claveDeObjeto);

        EvaluacionDeCaptura evaluacion;
        try {
            evaluacion = verificador.evaluar(claveDeObjeto, lado);
        } catch (KycServiceNoDisponibleException e) {
            // La foto se conserva: el operador que revise el caso la necesita.
            registrar(solicitudId, lado, claveDeObjeto, hash, objeto);
            fallos.derivarPorServicioNoDisponible(solicitudId);
            return ResultadoDeCaptura.diferida();
        }

        if (evaluacion.aceptada()) {
            registrar(solicitudId, lado, claveDeObjeto, hash, objeto);
            return ResultadoDeCaptura.aceptada();
        }

        almacen.eliminar(claveDeObjeto);
        return ResultadoDeCaptura.rechazada(evaluacion.motivo(), fallos.registrarFalloDeUsuario(solicitudId, lado));
    }

    private void registrar(UUID solicitudId, TipoDeDocumentoKyc lado, String claveDeObjeto, String hash,
                           ObjetoAlmacenado objeto) {
        documentos.guardar(new DocumentoKyc(
                UUID.randomUUID(), solicitudId, lado, claveDeObjeto, hash,
                Objects.requireNonNullElse(objeto.tipoDeContenido(), TIPO_DESCONOCIDO),
                objeto.tamanoBytes(), reloj.instant()));
    }
}
