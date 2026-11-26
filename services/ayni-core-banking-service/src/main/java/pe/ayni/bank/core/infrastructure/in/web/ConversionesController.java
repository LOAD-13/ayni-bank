package pe.ayni.bank.core.infrastructure.in.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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
import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeCambioCambioException;
import pe.ayni.bank.core.domain.port.in.ConfirmarConversionUseCase;
import pe.ayni.bank.core.domain.port.in.CotizarConversionUseCase;

/**
 * Cambio de moneda entre las cuentas del titular en soles y en dolares (HU-12).
 *
 * <p>Primero se cotiza, sin mover dinero, y la pantalla enseña el tipo aplicado, la referencia
 * oficial y el margen. Despues se confirma enviando el tipo que se vio: el servidor vuelve a
 * cotizar y, si ya no es ese, responde 409 con la cotizacion nueva. La identidad sale de la
 * cabecera que pone el gateway, como en el resto de operaciones.
 */
@RestController
public class ConversionesController {

    private static final String CODIGO = "codigo";
    private static final String CABECERA_USUARIO = OperacionesController.CABECERA_USUARIO;
    private static final String CABECERA_IDEMPOTENCIA = OperacionesController.CABECERA_IDEMPOTENCIA;
    private static final String IMPORTE = "^\\d{1,7}(\\.\\d{1,2})?$";
    private static final String TIPO = "^\\d{1,3}(\\.\\d{1,6})?$";

    private final CotizarConversionUseCase cotizar;
    private final ConfirmarConversionUseCase confirmar;

    public ConversionesController(CotizarConversionUseCase cotizar, ConfirmarConversionUseCase confirmar) {
        this.cotizar = cotizar;
        this.confirmar = confirmar;
    }

    /** No mueve dinero. Sin {@code importe} solo da el tipo; con el, tambien lo que se recibiria. */
    @GetMapping("/api/v1/tipo-cambio/cotizacion")
    public CotizacionDto cotizacion(@RequestHeader(CABECERA_USUARIO) UUID usuarioId,
                                    @RequestParam Moneda origen,
                                    @RequestParam(required = false) @Pattern(regexp = IMPORTE) String importe) {
        Cotizacion cotizacion = cotizar.cotizar(usuarioId, origen);
        Dinero entregado = importe == null ? null : new Dinero(new BigDecimal(importe), origen);
        return CotizacionDto.desde(cotizacion, origen, entregado,
                entregado == null ? null : cotizacion.tipo().convertir(entregado));
    }

    @PostMapping("/api/v1/conversiones")
    public ResponseEntity<ConversionDto> convertir(@RequestHeader(CABECERA_USUARIO) UUID usuarioId,
                                                   @RequestHeader(CABECERA_IDEMPOTENCIA) UUID clave,
                                                   @Valid @RequestBody SolicitudDeConversion solicitud) {
        Conversion conversion = confirmar.confirmar(usuarioId,
                new Dinero(new BigDecimal(solicitud.importe()), solicitud.origen()),
                new BigDecimal(solicitud.tipoMostrado()), Boolean.TRUE.equals(solicitud.enCacheMostrado()), clave);
        return ResponseEntity.created(URI.create("/api/v1/movimientos/" + conversion.salida().id()))
                .body(ConversionDto.desde(conversion));
    }

    // ── Errores ───────────────────────────────────────────────────────────

    /** 409 con la cotizacion nueva: el cliente la revisa y, si le sirve, confirma otra vez. */
    @ExceptionHandler(TipoDeCambioCambioException.class)
    public ResponseEntity<ProblemDetail> alCambiarElTipo(TipoDeCambioCambioException cambio) {
        Cotizacion nueva = cambio.nueva();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, cambio.getMessage());
        problema.setTitle("El tipo de cambio cambio");
        problema.setProperty(CODIGO, "TIPO_DE_CAMBIO_CAMBIO");
        problema.setProperty("cotizacion", CotizacionDto.desde(nueva, cambio.origen(), null, null));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
    }

    /** 503: no hay tipo oficial ni ultimo conocido. Nunca se convierte con un tipo inventado. */
    @ExceptionHandler(FuenteDeTipoDeCambioNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> sinTipoDeCambio() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No hay un tipo de cambio disponible ahora. Intenta de nuevo en unos minutos.");
        problema.setTitle("Tipo de cambio no disponible");
        problema.setProperty(CODIGO, "TIPO_DE_CAMBIO_NO_DISPONIBLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problema);
    }

    @ExceptionHandler(OperacionRechazadaException.class)
    public ResponseEntity<ProblemDetail> alRechazar(OperacionRechazadaException rechazo) {
        MotivoDeRechazo motivo = rechazo.motivo();
        HttpStatus estado = motivo == MotivoDeRechazo.SIN_CUENTA
                ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_ENTITY;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, motivo.detalle());
        problema.setTitle(motivo.titulo());
        problema.setProperty(CODIGO, motivo.name());
        return ResponseEntity.status(estado).body(problema);
    }

    @ExceptionHandler(ClaveDeIdempotenciaReutilizadaException.class)
    public ResponseEntity<ProblemDetail> alReutilizarClave() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Esa clave de idempotencia ya se uso en otra operacion.");
        problema.setTitle("Operacion duplicada");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema);
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

    // ── Contrato ──────────────────────────────────────────────────────────

    /** Importe y tipo llegan como texto: un numero JSON pasaria por coma flotante en el cliente. */
    public record SolicitudDeConversion(
            @NotNull Moneda origen,
            @NotBlank @Pattern(regexp = IMPORTE, message = "Importe no valido") String importe,
            @NotBlank @Pattern(regexp = TIPO, message = "Tipo no valido") String tipoMostrado,
            Boolean enCacheMostrado) {
    }

    /**
     * @param tipoAplicado soles por dolar que se le aplicaria, con el margen
     * @param tipoReferencia soles por dolar oficial del que sale
     * @param margen el margen total del segmento, en porcentaje: {@code 0.50}
     * @param importeDestino lo que recibiria, si se pidio con importe
     * @param enCache el tipo es el ultimo conocido y no el de hoy: la pantalla debe avisarlo
     */
    public record CotizacionDto(String origen, String destino, String tipoAplicado, String tipoReferencia,
                                String margen, String segmento, String fuente, String obtenidoEn,
                                boolean enCache, String importeOrigen, String importeDestino) {

        static CotizacionDto desde(Cotizacion c, Moneda origen, Dinero entregado, Dinero recibido) {
            return new CotizacionDto(origen.name(), (origen == Moneda.PEN ? Moneda.USD : Moneda.PEN).name(),
                    c.tipo().solesPorDolar().toPlainString(), c.referencia().toPlainString(),
                    comoPorcentaje(c.segmento().spread()), c.segmento().codigo(), c.fuente(),
                    c.tipo().obtenidoEn().toString(), c.tipo().enCache(),
                    entregado == null ? null : entregado.importe().toPlainString(),
                    recibido == null ? null : recibido.importe().toPlainString());
        }
    }

    public record ConversionDto(String conversionId, String movimientoId, String importeOrigen,
                                String monedaOrigen, String importeDestino, String monedaDestino,
                                String tipoAplicado, String tipoReferencia, String margen, String segmento,
                                String fuente, String tipoObtenidoEn, boolean enCache, String registradaEn) {

        static ConversionDto desde(Conversion c) {
            return new ConversionDto(c.id().toString(), c.salida().id().toString(),
                    c.importeOrigen().importe().toPlainString(), c.importeOrigen().moneda().name(),
                    c.importeDestino().importe().toPlainString(), c.importeDestino().moneda().name(),
                    c.tipo().solesPorDolar().toPlainString(), c.cotizacion().referencia().toPlainString(),
                    comoPorcentaje(c.cotizacion().spread()), c.cotizacion().segmento(), c.cotizacion().fuente(),
                    c.tipo().obtenidoEn().toString(), c.tipo().enCache(), c.registradaEn().toString());
        }
    }

    /** El margen se guarda como fraccion (0.005000) y se muestra como porcentaje (0.50). */
    private static String comoPorcentaje(BigDecimal fraccion) {
        return fraccion.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
