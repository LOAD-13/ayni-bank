package pe.ayni.bank.core.infrastructure.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.ControlesDeTarjeta;
import pe.ayni.bank.core.domain.model.CvvDinamico;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.PanRevelado;
import pe.ayni.bank.core.domain.model.SinTarjetaException;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.in.ConsultarTarjetaUseCase;
import pe.ayni.bank.core.domain.port.in.ControlarTarjetaUseCase;

/**
 * La tarjeta de debito virtual del titular y sus controles · HU-09 y HU-10.
 *
 * <p>Como el resto de la banca, el titular sale de la cabecera {@value #CABECERA_USUARIO} que
 * fija el gateway: no hay ruta con un identificador que cambiar. Por defecto solo viajan los
 * ultimos 4 digitos; el numero completo, solo por {@code POST /revelacion} con segundo factor.
 * El CVV y el PAN se responden con {@code Cache-Control: no-store}: ningun intermediario ni
 * el navegador deben guardarlos.
 */
@RestController
@RequestMapping("/api/v1/tarjetas/mia")
public class TarjetaController {

    static final String CABECERA_USUARIO = "X-Ayni-Usuario";
    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";
    static final String CABECERA_CONFIRMACION = "X-Ayni-Confirmacion";
    private static final String PATRON_LIMITE = "^[0-9]{1,7}\\.[0-9]{2}$";

    private final ConsultarTarjetaUseCase tarjetas;
    private final ControlarTarjetaUseCase controles;

    public TarjetaController(ConsultarTarjetaUseCase tarjetas) {
        this(tarjetas, null);
    }

    @Autowired
    public TarjetaController(ConsultarTarjetaUseCase tarjetas, ControlarTarjetaUseCase controles) {
        this.tarjetas = tarjetas;
        this.controles = controles;
    }

    @GetMapping
    public ResponseEntity<TarjetaDto> mia(@RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        return tarjetas.deTitular(usuarioId)
                .map(t -> ResponseEntity.ok(TarjetaDto.desde(t)))
                .orElseThrow(SinTarjetaException::new);
    }

    @GetMapping("/cvv")
    public ResponseEntity<CvvDto> cvv(@RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        CvvDinamico cvv = tarjetas.cvv(usuarioId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CvvDto(cvv.cvv(), cvv.validoHasta()));
    }

    @PostMapping("/revelacion")
    public ResponseEntity<PanReveladoDto> revelar(@RequestHeader(CABECERA_USUARIO) UUID usuarioId,
                                                  @RequestHeader(CABECERA_IDEMPOTENCIA) UUID clave,
                                                  @RequestHeader(value = CABECERA_CONFIRMACION, required = false)
                                                  String confirmacion,
                                                  HttpServletRequest peticion) {
        PanRevelado pan = tarjetas.revelar(usuarioId, confirmacion, clave, ipDe(peticion));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PanReveladoDto(pan.numero(), pan.visibleHasta()));
    }

    @GetMapping("/controles")
    public ResponseEntity<ControlesDeTarjetaDto> consultarControles(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        return ResponseEntity.ok(ControlesDeTarjetaDto.desde(controles.consultarControles(usuarioId)));
    }

    @PutMapping("/controles")
    public ResponseEntity<ControlesDeTarjetaDto> configurar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId,
            @Valid @RequestBody ConfiguracionDeTarjetaDto solicitud) {
        Dinero limite = new Dinero(new BigDecimal(solicitud.limiteDiario()), Moneda.PEN);
        if (!limite.esPositivo()) {
            throw new IllegalArgumentException("El limite diario debe ser mayor que cero.");
        }
        ControlesDeTarjeta actualizado = controles.configurar(
                usuarioId, limite, solicitud.comprasPorInternet());
        return ResponseEntity.ok(ControlesDeTarjetaDto.desde(actualizado));
    }

    @PostMapping("/congelamiento")
    public ResponseEntity<ControlesDeTarjetaDto> congelar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        return ResponseEntity.ok(ControlesDeTarjetaDto.desde(controles.congelar(usuarioId)));
    }

    @DeleteMapping("/congelamiento")
    public ResponseEntity<ControlesDeTarjetaDto> descongelar(
            @RequestHeader(CABECERA_USUARIO) UUID usuarioId) {
        return ResponseEntity.ok(ControlesDeTarjetaDto.desde(controles.descongelar(usuarioId)));
    }

    /** La IP del cliente: el primer valor de X-Forwarded-For, como en identity. */
    private static String ipDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return peticion.getRemoteAddr();
    }

    // ── Errores ───────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> argumentoInvalido(IllegalArgumentException error) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
        problema.setTitle("Configuracion no valida");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    /** 404: la web lo entiende como «tu tarjeta aun no esta lista». */
    @ExceptionHandler(SinTarjetaException.class)
    public ResponseEntity<ProblemDetail> sinTarjeta(SinTarjetaException falta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, falta.getMessage());
        problema.setTitle("Sin tarjeta");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problema);
    }

    /** 403: hay que confirmar con el segundo factor y reintentar con la misma clave. */
    @ExceptionHandler(ConfirmacionInvalidaException.class)
    public ResponseEntity<ProblemDetail> sinConfirmacion(ConfirmacionInvalidaException falta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Confirma con tu segundo factor para ver el numero completo.");
        problema.setTitle("Confirmacion requerida");
        problema.setProperty("codigo", "CONFIRMACION_REQUERIDA");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema);
    }

    /** Sin la cabecera de usuario la peticion no paso por el gateway: 401. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> sinCabecera(MissingRequestHeaderException falta) {
        boolean esIdentidad = CABECERA_USUARIO.equals(falta.getHeaderName());
        HttpStatus estado = esIdentidad ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, esIdentidad
                ? "Inicia sesion para continuar."
                : "Falta la cabecera " + falta.getHeaderName() + ".");
        problema.setTitle(esIdentidad ? "Sesion requerida" : "Peticion incompleta");
        return ResponseEntity.status(estado).body(problema);
    }

    // ── Contrato ──────────────────────────────────────────────────────────

    /** Solo los ultimos 4 digitos: el numero completo nunca viaja aqui. */
    public record TarjetaDto(String ultimos4, String numeroEnmascarado, String vencimiento, String estado) {
        static TarjetaDto desde(Tarjeta t) {
            return new TarjetaDto(t.pan().ultimos4(), t.pan().enmascarado(), t.vencimiento(), t.estado().name());
        }
    }

    public record CvvDto(String cvv, Instant validoHasta) {
    }

    public record PanReveladoDto(String numero, Instant visibleHasta) {

        @Override
        public String toString() {
            return "PanReveladoDto[oculto]";
        }
    }

    public record ConfiguracionDeTarjetaDto(
            @NotBlank @Pattern(regexp = PATRON_LIMITE, message = "El limite diario debe tener dos decimales.")
            String limiteDiario,
            @NotNull Boolean comprasPorInternet) {
    }

    public record ControlesDeTarjetaDto(
            String estado,
            String ultimos4,
            String moneda,
            String limiteDiario,
            String consumidoHoy,
            boolean comprasPorInternet) {

        static ControlesDeTarjetaDto desde(ControlesDeTarjeta c) {
            return new ControlesDeTarjetaDto(
                    c.estado().name(),
                    c.ultimos4(),
                    c.moneda().name(),
                    c.limiteDiario().importe().toPlainString(),
                    c.consumidoHoy().importe().toPlainString(),
                    c.comprasPorInternet());
        }
    }
}
