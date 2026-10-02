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
import pe.ayni.bank.identity.domain.port.in.ConfirmarDatosDelDniUseCase;
import pe.ayni.bank.identity.domain.port.in.EvaluarCapturaDeDniUseCase;
import pe.ayni.bank.identity.domain.port.in.ExtraerDatosDelDniUseCase;
import pe.ayni.bank.identity.domain.port.in.GenerarUrlDeSubidaUseCase;

/**
 * HU-02 · Verificacion del DNI. El navegador sube cada foto directamente a MinIO con el
 * formulario que firma este controlador; la imagen nunca atraviesa este servicio (ver
 * diseno-base.md §3.4-3.5 y §4.1).
 *
 * <p>El flujo: pedir el formulario de subida → subir → pedir la evaluacion de la foto, para
 * el anverso y para el reverso → pedir la lectura de los datos → confirmarlos. Todos los
 * resultados de negocio (aceptado, rechazado, en revision) responden 200 con un {@code estado}:
 * no son errores, son pasos del flujo. Ver ADR-0028.
 */
@RestController
@RequestMapping("/api/v1/solicitudes")
public class DocumentoKycController {

    private final GenerarUrlDeSubidaUseCase generarUrlDeSubida;
    private final EvaluarCapturaDeDniUseCase evaluarCaptura;
    private final ExtraerDatosDelDniUseCase extraerDatos;
    private final ConfirmarDatosDelDniUseCase confirmarDatos;

    public DocumentoKycController(GenerarUrlDeSubidaUseCase generarUrlDeSubida,
                                  EvaluarCapturaDeDniUseCase evaluarCaptura,
                                  ExtraerDatosDelDniUseCase extraerDatos,
                                  ConfirmarDatosDelDniUseCase confirmarDatos) {
        this.generarUrlDeSubida = generarUrlDeSubida;
        this.evaluarCaptura = evaluarCaptura;
        this.extraerDatos = extraerDatos;
        this.confirmarDatos = confirmarDatos;
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

    /** Escenarios 1 a 5: se acepta, se rechaza con el motivo, se deriva o se difiere. */
    @PostMapping("/{solicitudId}/documentos")
    public ResponseEntity<ResultadoDeCapturaDto> evaluarCaptura(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody SolicitudDeEvaluacionDto solicitud) {

        return ResponseEntity.ok(ResultadoDeCapturaDto.desde(evaluarCaptura.evaluar(
                solicitudId, TipoDeDocumentoKyc.valueOf(solicitud.tipoDocumento()), solicitud.claveDeObjeto())));
    }

    @PostMapping("/{solicitudId}/documentos/extraccion")
    public ResponseEntity<ResultadoDeExtraccionDto> extraerDatos(@PathVariable UUID solicitudId) {
        return ResponseEntity.ok(ResultadoDeExtraccionDto.desde(extraerDatos.extraer(solicitudId)));
    }

    @PostMapping("/{solicitudId}/identidad/confirmacion")
    public ResponseEntity<EstadoDelPasoDto> confirmarDatos(
            @PathVariable UUID solicitudId,
            @Valid @RequestBody SolicitudDeConfirmacionDto solicitud) {

        return ResponseEntity.ok(new EstadoDelPasoDto(
                confirmarDatos.confirmar(solicitudId, solicitud.aDominio()).name()));
    }

    @ExceptionHandler(SolicitudNoExisteException.class)
    public ProblemDetail alNoExistirLaSolicitud(SolicitudNoExisteException excepcion) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setTitle("La solicitud no existe");
        problema.setDetail(excepcion.getMessage());
        return problema;
    }
}
