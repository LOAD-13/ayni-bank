package pe.ayni.bank.core.infrastructure.in.web;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.application.usecase.OperarCuentaService.ClaveDeIdempotenciaReutilizadaException;
import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
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
    private static final int MAXIMO_DE_MOVIMIENTOS = 50;
    private static final String IMPORTE = "^\\d{1,7}(\\.\\d{1,2})?$";

    private final RepositorioDeCuentasPort cuentas;
    private final LibroMayorPort libro;
    private final OperarCuentaUseCase operar;

    public OperacionesController(RepositorioDeCuentasPort cuentas, LibroMayorPort libro,
                                 OperarCuentaUseCase operar) {
        this.cuentas = cuentas;
        this.libro = libro;
        this.operar = operar;
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
            @Valid @RequestBody SolicitudDeTransferencia solicitud) {
        Comprobante comprobante = operar.transferir(usuarioId, solicitud.cuentaDestino(),
                soles(solicitud.importe()), solicitud.concepto(), clave);
        return creado(comprobante);
    }

    // ── Errores ───────────────────────────────────────────────────────────

    /** 422: la peticion esta bien formada pero una regla de negocio la impide. */
    @ExceptionHandler(OperacionRechazadaException.class)
    public ResponseEntity<ProblemDetail> alRechazar(OperacionRechazadaException rechazo) {
        MotivoDeRechazo motivo = rechazo.motivo();
        HttpStatus estado = motivo == MotivoDeRechazo.SIN_CUENTA
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNPROCESSABLE_ENTITY;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, motivo.detalle());
        problema.setTitle(motivo.titulo());
        problema.setProperty("codigo", motivo.name());
        return ResponseEntity.status(estado).body(problema);
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

    private static ResponseEntity<ComprobanteDto> creado(Comprobante comprobante) {
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
