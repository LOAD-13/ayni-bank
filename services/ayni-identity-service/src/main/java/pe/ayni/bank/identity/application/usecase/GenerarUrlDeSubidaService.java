package pe.ayni.bank.identity.application.usecase;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.port.in.GenerarUrlDeSubidaUseCase;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeMetodoSegundoFactorPort;
import pe.ayni.bank.identity.domain.port.out.RepositorioDeSegundoFactorPort;
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
    private final RepositorioDeMetodoSegundoFactorPort metodosSegundoFactor;
    private final RepositorioDeSegundoFactorPort segundosFactores;

    public GenerarUrlDeSubidaService(RepositorioDeSolicitudesPort solicitudes,
                                     AlmacenDeDocumentosPort almacen) {
        this(solicitudes, almacen, null, null);
    }

    @Autowired
    public GenerarUrlDeSubidaService(RepositorioDeSolicitudesPort solicitudes,
                                     AlmacenDeDocumentosPort almacen,
                                     @Autowired(required = false) RepositorioDeMetodoSegundoFactorPort metodosSegundoFactor,
                                     @Autowired(required = false) RepositorioDeSegundoFactorPort segundosFactores) {
        this.solicitudes = solicitudes;
        this.almacen = almacen;
        this.metodosSegundoFactor = metodosSegundoFactor;
        this.segundosFactores = segundosFactores;
    }

    @Override
    public UrlDeSubida generar(UUID solicitudId, TipoDeDocumentoKyc tipoDocumento, String extension) {
        UUID titularId = solicitudes.titularDe(solicitudId)
                .orElseThrow(() -> new SolicitudNoExisteException(
                        "La solicitud no existe o es un senuelo sin titular."));
        GuardiaDeSegundoFactorEnKyc.exigirVerificado(titularId, metodosSegundoFactor, segundosFactores);

        String extensionLimpia = sanitizarExtension(extension);

        String claveDeObjeto = "kyc/%s/%s%s.%s".formatted(
                solicitudId, tipoDocumento.prefijoDeObjeto(), UUID.randomUUID(), extensionLimpia);

        return almacen.generarUrlDeSubida(claveDeObjeto, tipoDocumento.tipoDeContenidoEsperado(extensionLimpia),
                TipoDeDocumentoKyc.TAMANO_MAXIMO_BYTES);
    }

    /**
     * Sanitiza y valida que la extensión pertenezca al catálogo permitido (jpg, jpeg, png, webp).
     * Sin PDF: kyc-service no puede analizarlo (ver {@link TipoDeDocumentoKyc#tipoDeContenidoEsperado}).
     *
     * @param extension Extensión recibida en el requerimiento.
     * @return Extensión limpia en minúsculas.
     */
    private String sanitizarExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "jpg";
        }
        String limpia = PATRON_CARACTERES_INVALIDOS.matcher(extension.trim().toLowerCase(Locale.ROOT)).replaceAll("");
        if (!EXTENSIONES_PERMITIDAS.contains(limpia)) {
            throw new IllegalArgumentException("La extensión del archivo debe ser jpg, jpeg, png o webp.");
        }
        return limpia;
    }
}
