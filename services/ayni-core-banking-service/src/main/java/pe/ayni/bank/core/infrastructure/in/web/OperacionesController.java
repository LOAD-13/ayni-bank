package pe.ayni.bank.core.infrastructure.in.web;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeMovimiento;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/**
 * La cuenta del titular que ha iniciado sesion: panel, movimientos, deposito simulado y
 * transferencias · HU-07 y HU-08.
 *
 * <p><strong>La identidad sale de la cabecera {@value #CABECERA_USUARIO}</strong>, que pone
 * el gateway despues de validar el JWT, y nunca de la URL ni del cuerpo. Por eso no hay un
 * solo identificador de usuario en estas rutas: no se puede pedir la cuenta de otro porque
 * no hay donde escribirlo. Un cliente no puede fabricar la cabecera: el gateway la borra de
 * toda peticion entrante.
 */
@RestController
public class OperacionesController {

    static final String CABECERA_USUARIO = "X-Ayni-Usuario";
    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";
    /** Token de confirmacion con segundo factor que emite identity (HU-07, ADR-0031). */
    static final String CABECERA_CONFIRMACION = "X-Ayni-Confirmacion";
    private static final int MAXIMO_DE_MOVIMIENTOS = 50;
    private static final String IMPORTE = "^\\d{1,7}(\\.\\d{1,2})?$";

    private final RepositorioDeCuentasPort cuentas;
    private final LibroMayorPort libro;
    private final OperarCuentaUseCase operar;
    private final MetricasDeOperaciones metricas;

    public OperacionesController(RepositorioDeCuentasPort cuentas, LibroMayorPort libro,
                                 OperarCuentaUseCase operar, MetricasDeOperaciones metricas) {
        this.cuentas = cuentas;
        this.libro = libro;
        this.operar = operar;
        this.metricas = metricas;
    }

    @GetMapping("/api/v1/cuentas/mia")
    public CuentaDto miCuenta(@RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        Cuenta cuenta = cuentaDe(usuarioId);
        return CuentaDto.desde(cuenta,
                libro.saldoDe(cuenta.id(), cuenta.moneda()),
                cuentas.treaVigenteDe(cuenta.productoId()).orElse(null));
    }

    @GetMapping("/api/v1/cuentas/mia/movimientos")
    public List<MovimientoDto> misMovimientos(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @RequestParam(defaultValue = "20") int limite) {
        Cuenta cuenta = cuentaDe(usuarioId);
        int acotado = Math.clamp(limite, 1, MAXIMO_DE_MOVIMIENTOS);
        return libro.ultimosAsientosDe(cuenta.id(), acotado).stream()
                .map(MovimientoDto::desde)
                .toList();
    }

    @PostMapping("/api/v1/cuentas/mia/depositos-simulados")
    public ResponseEntity<ComprobanteDto> depositar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @RequestHeader(CABECERA_IDEMPOTENCIA) UUID clave,
            @Valid @RequestBody SolicitudDeDeposito solicitud) {
        Comprobante comprobante = operar.depositarSimulado(
                usuarioId, soles(solicitud.importe()), clave);
        return creado(comprobante);
    }

    @PostMapping("/api/v1/transferencias")
    public ResponseEntity<ComprobanteDto> transferir(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @RequestHeader(CABECERA_IDEMPOTENCIA) UUID clave,
            @RequestHeader(value = CABECERA_CONFIRMACION, required = false) String confirmacion,
            @Valid @RequestBody SolicitudDeTransferencia solicitud) {
        Comprobante comprobante = operar.transferir(usuarioId, solicitud.cuentaDestino(),
                soles(solicitud.importe()), solicitud.concepto(), clave, confirmacion);
        return creado(comprobante);
    }

    /** HU-07: el comprobante de un movimiento propio. 404 si no existe o no es del titular. */
    @GetMapping("/api/v1/movimientos/{movimientoId}")
    public ResponseEntity<ComprobanteDto> comprobante(@RequestHeader(CABECERA_USUARIO) UUID usuarioId,
                                                      @PathVariable UUID movimientoId) {
        return operar.comprobante(usuarioId, movimientoId)
                .map(c -> ResponseEntity.ok(ComprobanteDto.desde(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** HU-07: el mismo comprobante en PDF, para descargar o reenviar. */
    @GetMapping(value = "/api/v1/movimientos/{movimientoId}/comprobante.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> comprobanteEnPdf(@RequestHeader(CABECERA_USUARIO) UUID usuarioId,
                                                   @PathVariable UUID movimientoId) {
        return operar.comprobante(usuarioId, movimientoId)
                .map(c -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename("comprobante-ayni-" + movimientoId + ".pdf").build().toString())
                        // Un comprobante bancario no se queda en la cache de un equipo compartido.
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .body(ComprobanteEnPdf.generar(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Errores ───────────────────────────────────────────────────────────

    /** 422: la peticion esta bien formada pero una regla de negocio la impide. */
    @ExceptionHandler(OperacionRechazadaException.class)
    public ResponseEntity<ProblemDetail> alRechazar(OperacionRechazadaException rechazo,
                                                    HttpServletRequest peticion) {
        MotivoDeRechazo motivo = rechazo.motivo();
        if ("POST".equals(peticion.getMethod())) {
            metricas.rechazada(peticion.getRequestURI().endsWith("/depositos-simulados")
                    ? TipoDeMovimiento.DEPOSITO_SIMULADO
                    : TipoDeMovimiento.TRANSFERENCIA, motivo);
        }
        HttpStatus estado = motivo == MotivoDeRechazo.SIN_CUENTA
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNPROCESSABLE_ENTITY;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, motivo.detalle());
        problema.setTitle(motivo.titulo());
        problema.setProperty("codigo", motivo.name());
        return ResponseEntity.status(estado).body(problema);
    }

    /**
     * 403: la sesion es valida, pero esta transferencia no esta confirmada con el segundo
     * factor. El cliente debe pedir la confirmacion a identity y reintentar con la misma
     * clave de idempotencia.
     */
    @ExceptionHandler(ConfirmacionInvalidaException.class)
    public ResponseEntity<ProblemDetail> alFaltarConfirmacion(ConfirmacionInvalidaException falta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, falta.getMessage());
        problema.setTitle("Confirmacion requerida");
        problema.setProperty("codigo", "CONFIRMACION_REQUERIDA");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema);
    }

    /** Sin la cabecera de usuario la peticion no paso por el gateway: 401. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> alFaltarCabecera(MissingRequestHeaderException falta) {
        boolean esIdentidad = CABECERA_USUARIO.equals(falta.getHeaderName());
        HttpStatus estado = esIdentidad ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, esIdentidad
                ? "Inicia sesion para continuar."
                : "Falta la cabecera " + falta.getHeaderName() + ".");
        problema.setTitle(esIdentidad ? "Sesion requerida" : "Peticion incompleta");
        return ResponseEntity.status(estado).body(problema);
    }

    @ExceptionHandler(ClaveDeIdempotenciaReutilizadaException.class)
    public ResponseEntity<ProblemDetail> alReutilizarClave() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Esa clave de idempotencia ya se uso en otra operacion.");
        problema.setTitle("Operacion duplicada");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }

    private Cuenta cuentaDe(UUID usuarioId) {
        return cuentas.buscarActivaDe(usuarioId, Moneda.PEN)
                .orElseThrow(() -> new OperacionRechazadaException(MotivoDeRechazo.SIN_CUENTA));
    }

    private static Dinero soles(String importe) {
        return new Dinero(new BigDecimal(importe), Moneda.PEN);
    }

    private ResponseEntity<ComprobanteDto> creado(Comprobante comprobante) {
        metricas.aceptada(comprobante);
        return ResponseEntity
                .created(URI.create("/api/v1/movimientos/" + comprobante.movimientoId()))
                .body(ComprobanteDto.desde(comprobante));
    }

    // ── Contrato ──────────────────────────────────────────────────────────

    /** El importe llega como texto: un numero JSON pasaria por coma flotante en el cliente. */
    public record SolicitudDeDeposito(
            @NotBlank @Pattern(regexp = IMPORTE, message = "Importe no valido") String importe) {
    }

    public record SolicitudDeTransferencia(
            @NotBlank @Size(max = 20) String cuentaDestino,
            @NotBlank @Pattern(regexp = IMPORTE, message = "Importe no valido") String importe,
            @Size(max = 60) String concepto) {
    }

    public record MovimientoDto(String movimientoId, String tipo, String importe, String moneda,
                                String concepto, String registradoEn) {

        static MovimientoDto desde(Asiento asiento) {
            return new MovimientoDto(asiento.movimientoId().toString(), asiento.tipo().name(),
                    asiento.importe().importe().toPlainString(), asiento.importe().moneda().name(),
                    asiento.concepto(), asiento.registradoEn().toString());
        }
    }

    public record ComprobanteDto(String movimientoId, String tipo, String importe, String moneda,
                                 String cuentaOrigen, String cuentaDestino, String concepto,
                                 String registradoEn, String saldoDisponible) {

        static ComprobanteDto desde(Comprobante c) {
            return new ComprobanteDto(c.movimientoId().toString(), c.tipo().name(),
                    c.importe().importe().toPlainString(), c.importe().moneda().name(),
                    c.cuentaOrigen(), c.cuentaDestino(), c.concepto(),
                    c.registradoEn().toString(), c.saldoDisponible().importe().toPlainString());
        }
    }
}
