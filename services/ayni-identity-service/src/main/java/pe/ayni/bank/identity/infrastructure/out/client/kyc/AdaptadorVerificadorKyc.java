package pe.ayni.bank.identity.infrastructure.out.client.kyc;

import java.util.Optional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import pe.ayni.bank.identity.domain.model.DatosDelDni;
import pe.ayni.bank.identity.domain.model.EvaluacionDeCaptura;
import pe.ayni.bank.identity.domain.model.FuenteDeLectura;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.LecturaDelDni;
import pe.ayni.bank.identity.domain.model.MotivoDeRechazoDeCaptura;
import pe.ayni.bank.identity.domain.model.TipoDeDocumentoKyc;
import pe.ayni.bank.identity.domain.port.out.VerificadorKycPort;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.DocumentosApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.DatosExtraidos;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.EvaluacionRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.EvaluacionResponse;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.ExtraccionRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.ExtraccionResponse;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.LadoDelDocumento;

/**
 * Implementa VerificadorKycPort envolviendo DocumentosApi (cliente generado desde
 * {@code contracts/kyc-service.openapi.yaml}) con Retry + CircuitBreaker (ADR-0020, ADR-0028).
 *
 * <p><strong>El orden de las anotaciones importa, y no es el que aplica por
 * defecto.</strong> Se necesita CircuitBreaker decorando a Retry (afuera),
 * no al reves: si los reintentos internos fallan todos, CircuitBreaker
 * debe ver UNA sola llamada fallida. Se confirmo experimentalmente que el
 * orden por defecto de resilience4j-spring-boot3 2.4.0 es el contrario, asi
 * que {@code circuit-breaker-aspect-order}/{@code retry-aspect-order} se
 * fijan explicitamente en application.yml. Ver ADR-0020.
 *
 * <p><strong>El fallback se dispara para CUALQUIER excepcion</strong> que
 * propague el metodo decorado, sin importar {@code record-exceptions}/
 * {@code ignore-exceptions} (esas propiedades solo afectan las metricas del
 * circuito, no si el fallback se invoca). Por eso {@link #relanzarONoDisponible}
 * distingue el tipo de causa en vez de asumir que todo error implica que el
 * servicio no esta disponible.
 */
@Component
public class AdaptadorVerificadorKyc implements VerificadorKycPort {

    private static final Logger log = LoggerFactory.getLogger(AdaptadorVerificadorKyc.class);

    private final DocumentosApi documentosApi;
    private final DocumentosApi documentosApiDeExtraccion;

    /**
     * Dos clientes porque las dos operaciones no tienen la misma espera: evaluar una foto es
     * inmediato (10 s de timeout), leerla con OCR no (ver ConfiguracionDelClienteKyc).
     */
    public AdaptadorVerificadorKyc(DocumentosApi documentosApi,
                                   @Qualifier("documentosApiDeExtraccion") DocumentosApi documentosApiDeExtraccion) {
        this.documentosApi = documentosApi;
        this.documentosApiDeExtraccion = documentosApiDeExtraccion;
    }

    @Override
    @CircuitBreaker(name = "kycService", fallbackMethod = "alNoPoderEvaluar")
    @Retry(name = "kycService")
    public EvaluacionDeCaptura evaluar(String claveDeObjeto, TipoDeDocumentoKyc lado) {
        EvaluacionResponse respuesta = documentosApi.evaluarCaptura(new EvaluacionRequest()
                .documentKey(claveDeObjeto)
                .lado(LadoDelDocumento.fromValue(lado.name())));

        if (Boolean.TRUE.equals(respuesta.getAceptada())) {
            return EvaluacionDeCaptura.aprobada();
        }
        // Un rechazo sin motivo no deberia llegar (el contrato lo exige), pero si llega no se
        // acepta la foto: se le pide al solicitante que la repita como si no fuera un DNI.
        MotivoDeRechazoDeCaptura motivo = respuesta.getMotivoRechazo() == null
                ? MotivoDeRechazoDeCaptura.NO_ES_DNI
                : MotivoDeRechazoDeCaptura.valueOf(respuesta.getMotivoRechazo().getValue());
        return EvaluacionDeCaptura.rechazada(motivo);
    }

    @Override
    @CircuitBreaker(name = "kycService", fallbackMethod = "alNoPoderExtraer")
    @Retry(name = "kycService")
    public Optional<LecturaDelDni> extraer(String claveAnverso, String claveReverso) {
        ExtraccionResponse respuesta = documentosApiDeExtraccion.extraerDatos(new ExtraccionRequest()
                .anversoDocumentKey(claveAnverso)
                .reversoDocumentKey(claveReverso));

        if (!Boolean.TRUE.equals(respuesta.getLegible()) || respuesta.getDatos() == null) {
            return Optional.empty();
        }
        return aLectura(respuesta.getDatos());
    }

    /**
     * Datos que no pasan las reglas del dominio (un sexo "<" del MRZ, un numero que no son ocho
     * digitos) cuentan como una lectura fallida: el solicitante repite la foto, en lugar de
     * recibir un error tecnico.
     */
    private static Optional<LecturaDelDni> aLectura(DatosExtraidos datos) {
        // El contrato marca la fuente como obligatoria, pero lo que llega por la red no se
        // da por bueno: sin fuente no hay lectura.
        Optional<FuenteDeLectura> fuente = Optional.ofNullable(datos.getFuente())
                .map(f -> FuenteDeLectura.valueOf(f.getValue()));
        if (fuente.isEmpty()) {
            return Optional.empty();
        }
        try {
            DatosDelDni dni = new DatosDelDni(datos.getDni(), datos.getNombres(), datos.getApellidos(),
                    datos.getFechaNacimiento(), datos.getSexo(), datos.getFechaEmision());
            return Optional.of(new LecturaDelDni(dni, fuente.get(),
                    Boolean.TRUE.equals(datos.getConfiable())));
        } catch (IllegalArgumentException e) {
            log.warn("kyc-service devolvio datos del DNI que no cumplen las reglas del dominio: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Firmas exigidas por Resilience4j: mismos parametros del metodo decorado mas un
     * {@link Throwable} final.
     */
    private EvaluacionDeCaptura alNoPoderEvaluar(String claveDeObjeto, TipoDeDocumentoKyc lado, Throwable causa) {
        throw relanzarONoDisponible(causa);
    }

    private Optional<LecturaDelDni> alNoPoderExtraer(String claveAnverso, String claveReverso, Throwable causa) {
        throw relanzarONoDisponible(causa);
    }

    /**
     * Un {@link HttpClientErrorException} (4xx) no es un problema de disponibilidad del
     * servicio — es una peticion mal formada (una clave que no existe en el bucket) — asi
     * que se relanza tal cual, sin reintento (ya excluido de {@code retry-exceptions}) ni
     * conversion a "servicio no disponible". Cualquier otra causa (5xx, timeout, error de
     * conexion, o {@code CallNotPermittedException} con el circuito abierto) si se traduce a
     * {@link KycServiceNoDisponibleException}.
     */
    private static RuntimeException relanzarONoDisponible(Throwable causa) {
        if (causa instanceof HttpClientErrorException errorDeCliente) {
            return errorDeCliente;
        }
        return new KycServiceNoDisponibleException(
                "kyc-service no respondio tras agotar los reintentos configurados.", causa);
    }
}
