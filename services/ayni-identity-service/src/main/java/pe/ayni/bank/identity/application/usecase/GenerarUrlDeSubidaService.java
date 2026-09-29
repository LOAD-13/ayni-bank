package pe.ayni.bank.identity.application.usecase;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
 */
@Service
public class GenerarUrlDeSubidaService implements GenerarUrlDeSubidaUseCase {

    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Pattern PATRON_CARACTERES_INVALIDOS = Pattern.compile("[^a-z0-9]");

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

        String extensionLimpia = sanitizarExtension(extension);

        String claveDeObjeto = "kyc/%s/%s-%s.%s".formatted(
                solicitudId, tipoDocumento.name().toLowerCase(Locale.ROOT), UUID.randomUUID(), extensionLimpia);

        return almacen.generarUrlDeSubida(claveDeObjeto, tipoDocumento.tipoDeContenidoEsperado());
    }

    /**
     * Sanitiza y valida que la extensión pertenezca al catálogo permitido (jpg, jpeg, png, webp).
     *
     * @param extension Extensión recibida en el requerimiento.
     * @return Extensión limpia en minúsculas.
     */
    private String sanitizarExtension(String extension) {
        if (extension == null) {
            return "jpg";
        }
        String limpia = PATRON_CARACTERES_INVALIDOS.matcher(extension.trim().toLowerCase(Locale.ROOT)).replaceAll("");
        if (!EXTENSIONES_PERMITIDAS.contains(limpia)) {
            throw new IllegalArgumentException("La extensión del archivo debe ser jpg, jpeg, png o webp.");
        }
        return limpia;
    }
}


