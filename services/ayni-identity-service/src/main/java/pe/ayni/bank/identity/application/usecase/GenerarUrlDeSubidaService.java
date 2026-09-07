package pe.ayni.bank.identity.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.port.in.GenerarUrlDeSubidaUseCase;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSolicitudesPort;

/**
 * Genera la URL pre-firmada con la que el navegador sube un documento KYC
 * directamente a MinIO, sin que la imagen atraviese este servicio (ver
 * diseno-base.md §3.4-3.5 y §4.1).
 *
 * <p>No toca el estado de la solicitud ni escribe en {@code documento_kyc}:
 * esa migracion y su escritura son las subtareas 8 y 9 de AYNI-13, todavia
 * no implementadas. Esta subtarea es solo el mecanismo de subida en si.
 */
@Service
public class GenerarUrlDeSubidaService implements GenerarUrlDeSubidaUseCase {

    private final RepositorioDeSolicitudesPort solicitudes;
    private final AlmacenDeDocumentosPort almacen;

    public GenerarUrlDeSubidaService(RepositorioDeSolicitudesPort solicitudes,
                                     AlmacenDeDocumentosPort almacen) {
        this.solicitudes = solicitudes;
        this.almacen = almacen;
    }

    @Override
    public UrlDeSubida generar(UUID solicitudId, TipoDeDocumentoKyc tipoDocumento, String extension) {
        solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoExisteException(
                        "La solicitud no existe o es un senuelo sin titular."));

        String claveDeObjeto = "kyc/%s/%s-%s.%s".formatted(
                solicitudId, tipoDocumento.name().toLowerCase(), UUID.randomUUID(), extension);

        return almacen.generarUrlDeSubida(claveDeObjeto, tipoDocumento.tipoDeContenidoEsperado());
    }
}
