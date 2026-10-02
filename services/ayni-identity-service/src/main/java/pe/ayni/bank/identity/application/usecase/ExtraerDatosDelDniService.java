package pe.ayni.bank.identity.application.usecase;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pe.ayni.bank.identity.domain.model.CapturasIncompletasException;
import pe.ayni.bank.identity.domain.model.DocumentoKyc;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.ResultadoDeExtraccion;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.port.in.ExtraerDatosDelDniUseCase;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;
import pe.ayni.bank.identity.domain.port.out.VerificadorKycPort;

/**
 * HU-02 · Lee los datos del DNI de las dos fotos aceptadas (escenario 1, ADR-0026).
 *
 * <p>Antes de leer, comprueba que las fotos siguen siendo las que se evaluaron: la politica
 * de subida permite volver a escribir la misma clave durante cinco minutos, y el hash
 * guardado es lo que delata ese cambio.
 *
 * <p>Si el OCR no encuentra los datos, cuenta como un intento fallido del reverso: es la
 * cara que trae el MRZ, la fuente principal de la lectura, y la que hay que repetir.
 */
@Service
public class ExtraerDatosDelDniService implements ExtraerDatosDelDniUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExtraerDatosDelDniService.class);

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeDocumentosKycPort documentos;
    private final AlmacenDeDocumentosPort almacen;
    private final VerificadorKycPort verificador;
    private final GestionarFalloDeVerificacionKycUseCase fallos;

    public ExtraerDatosDelDniService(RepositorioDeSolicitudesPort solicitudes,
                                     RepositorioDeDocumentosKycPort documentos,
                                     AlmacenDeDocumentosPort almacen,
                                     VerificadorKycPort verificador,
                                     GestionarFalloDeVerificacionKycUseCase fallos) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.almacen = almacen;
        this.verificador = verificador;
        this.fallos = fallos;
    }

    @Override
    public ResultadoDeExtraccion extraer(UUID solicitudId) {
        solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoExisteException(
                        "La solicitud no existe o es un senuelo sin titular."));

        if (solicitudes.estaEnRevisionManual(solicitudId)) {
            return ResultadoDeExtraccion.enRevisionManual();
        }

        DocumentoKyc anverso = documentos.ultimoDe(solicitudId, TipoDeDocumentoKyc.ANVERSO)
                .orElseThrow(() -> new CapturasIncompletasException("Falta la foto del anverso del DNI."));
        DocumentoKyc reverso = documentos.ultimoDe(solicitudId, TipoDeDocumentoKyc.REVERSO)
                .orElseThrow(() -> new CapturasIncompletasException("Falta la foto del reverso del DNI."));

        if (!sigueIntacto(anverso) || !sigueIntacto(reverso)) {
            log.warn("Un documento KYC cambio despues de evaluarse. solicitudId={}", solicitudId);
            fallos.derivarPorDiscrepancia(solicitudId);
            return ResultadoDeExtraccion.enRevisionManual();
        }

        Optional<LecturaDelDni> lectura;
        try {
            lectura = verificador.extraer(anverso.claveDeObjeto(), reverso.claveDeObjeto());
        } catch (KycServiceNoDisponibleException e) {
            fallos.derivarPorServicioNoDisponible(solicitudId);
            return ResultadoDeExtraccion.diferida();
        }

        if (lectura.isEmpty()) {
            return ResultadoDeExtraccion.ilegible(
                    fallos.registrarFalloDeUsuario(solicitudId, TipoDeDocumentoKyc.REVERSO));
        }

        documentos.guardarLectura(solicitudId, lectura.get());
        return ResultadoDeExtraccion.leida(lectura.get());
    }

    private boolean sigueIntacto(DocumentoKyc documento) {
        return almacen.describir(documento.claveDeObjeto()).isPresent()
                && almacen.calcularHash(documento.claveDeObjeto()).equals(documento.hashSha256());
    }
}
