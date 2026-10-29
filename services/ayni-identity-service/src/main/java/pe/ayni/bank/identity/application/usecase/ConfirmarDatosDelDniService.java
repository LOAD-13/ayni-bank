package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.ayni.bank.identity.domain.model.CapturasIncompletasException;
import pe.ayni.bank.identity.domain.model.DatosConfirmados;
import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.EstadoDelPasoKyc;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.MetodoDeSegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactor;
import pe.ayni.bank.identity.domain.model.SegundoFactorNoVerificadoException;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.port.in.ConfirmarDatosDelDniUseCase;
import pe.ayni.bank.identity.domain.port.in.GestionarFalloDeVerificacionKycUseCase;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeDocumentosKycPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * HU-02 · El titular confirma o corrige lo que leyo el OCR (ADR-0009, ADR-0028).
 *
 * <p>Pasa a revision manual en dos casos:
 *
 * <ul>
 *   <li>lo confirmado no coincide con lo declarado en el paso 1;
 *   <li>el titular cambia el numero o la fecha de nacimiento de una lectura del MRZ con sus
 *       digitos verificadores validos. Esos dos campos no se leen mal por un reflejo: si la
 *       persona los cambia, no corrige al OCR, contradice al documento.
 * </ul>
 *
 * <p>Lo confirmado se guarda siempre, tambien si se deriva: el operador necesita ver lo que
 * leyo la maquina, lo que escribio la persona y lo que declaro al registrarse.
 */
@Service
public class ConfirmarDatosDelDniService implements ConfirmarDatosDelDniUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmarDatosDelDniService.class);

    private final RepositorioDeSolicitudesPort solicitudes;
    private final RepositorioDeDocumentosKycPort documentos;
    private final GestionarFalloDeVerificacionKycUseCase fallos;
    private final RepositorioDeMetodoSegundoFactorPort metodosSegundoFactor;
    private final RepositorioDeSegundoFactorPort segundosFactores;

    public ConfirmarDatosDelDniService(RepositorioDeSolicitudesPort solicitudes,
                                       RepositorioDeDocumentosKycPort documentos,
                                       GestionarFalloDeVerificacionKycUseCase fallos) {
        this(solicitudes, documentos, fallos, null, null);
    }

    @Autowired
    public ConfirmarDatosDelDniService(RepositorioDeSolicitudesPort solicitudes,
                                       RepositorioDeDocumentosKycPort documentos,
                                       GestionarFalloDeVerificacionKycUseCase fallos,
                                       @Autowired(required = false) RepositorioDeMetodoSegundoFactorPort metodosSegundoFactor,
                                       @Autowired(required = false) RepositorioDeSegundoFactorPort segundosFactores) {
        this.solicitudes = solicitudes;
        this.documentos = documentos;
        this.fallos = fallos;
        this.metodosSegundoFactor = metodosSegundoFactor;
        this.segundosFactores = segundosFactores;
    }

    @Override
    @Transactional
    public EstadoDelPasoKyc confirmar(UUID solicitudId, DatosConfirmados confirmados) {
        UUID titularId = solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoExisteException(
                        "La solicitud no existe o es un senuelo sin titular."));
        exigirSegundoFactorVerificado(titularId);

        if (solicitudes.estaEnRevisionManual(solicitudId)) {
            return EstadoDelPasoKyc.EN_REVISION_MANUAL;
        }

        LecturaDelDni leida = documentos.ultimaLecturaOcrDe(solicitudId)
                .orElseThrow(() -> new CapturasIncompletasException("Primero hay que leer los datos del DNI."));

        DatosDelDni datos = confirmados.completarCon(leida.datos());
        documentos.guardarLectura(solicitudId, new LecturaDelDni(datos, FuenteDeLectura.TITULAR, false));

        boolean contradiceAlDocumento = leida.confiable() && datos.contradiceLoVerificadoEn(leida.datos());
        boolean coincideConLoDeclarado = solicitudes.datosDeclaradosDe(solicitudId)
                .map(datos::coincideCon)
                .orElse(false);

        if (!contradiceAlDocumento && coincideConLoDeclarado) {
            solicitudes.marcarDocumentoCargado(solicitudId);
            log.info("DNI verificado y confirmado por el titular (corregido={}). solicitudId={}",
                    datos.difiereDe(leida.datos()), solicitudId);
            return EstadoDelPasoKyc.ACEPTADO;
        }

        fallos.derivarPorDiscrepancia(solicitudId);
        return EstadoDelPasoKyc.EN_REVISION_MANUAL;
    }

    private void exigirSegundoFactorVerificado(UUID titularId) {
        if (metodosSegundoFactor == null && segundosFactores == null) {
            return;
        }
        boolean verificadoPorMetodo = metodosSegundoFactor != null
                && metodosSegundoFactor.listarPorUsuario(titularId).stream()
                        .anyMatch(MetodoDeSegundoFactor::estaConfirmado);
        boolean verificadoPorTotp = segundosFactores != null
                && segundosFactores.buscarPorUsuario(titularId)
                        .filter(SegundoFactor::estaConfirmado)
                        .isPresent();
        if (!verificadoPorMetodo && !verificadoPorTotp) {
            throw new SegundoFactorNoVerificadoException();
        }
    }
}
