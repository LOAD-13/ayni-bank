package pe.ayni.bank.identity.infrastructure.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pe.ayni.bank.identity.domain.model.SolicitudNoExisteException;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.port.in.GenerarUrlDeSubidaUseCase;

/**
 * HU-02 · Subida de documentos KYC. El navegador sube directamente a MinIO
 * con la URL que este endpoint firma; la imagen nunca atraviesa este
 * servicio (ver diseno-base.md §3.4-3.5 y §4.1).
 */
@RestController
@RequestMapping("/api/v1/solicitudes")
public class DocumentoKycController {

    private final GenerarUrlDeSubidaUseCase generarUrlDeSubida;

    public DocumentoKycController(GenerarUrlDeSubidaUseCase generarUrlDeSubida) {
        this.generarUrlDeSubida = generarUrlDeSubida;
    }

    @PostMapping("/{solicitudId}/documentos/url-de-subida")
    public ResponseEntity<UrlDeSubidaDto> obtenerUrlDeSubida(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody SolicitudDeUrlDeSubidaDto solicitud) {

        UrlDeSubida resultado = generarUrlDeSubida.generar(
                solicitudId,
                TipoDeDocumentoKyc.valueOf(solicitud.tipoDocumento()),
                solicitud.extension());

        return ResponseEntity.ok(UrlDeSubidaDto.desde(resultado));
    }

    @ExceptionHandler(SolicitudNoExisteException.class)
    public ProblemDetail alNoExistirLaSolicitud(SolicitudNoExisteException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("La solicitud no existe");
        problema.setDetail(excepcion.getMessage());
        return problema;
    }
}
