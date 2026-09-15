package pe.ayni.bank.identity.infrastructure.out.client.kyc;

import java.util.UUID;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import pe.ayni.bank.identity.domain.model.EstadoDeVerificacionKyc;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.ResultadoDeVerificacionKyc;
import pe.ayni.bank.identity.domain.port.out.VerificadorKycPort;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.VerificationApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.VerificationRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.VerificationResponse;

/**
 * Implementa VerificadorKycPort envolviendo VerificationApi (cliente generado,
 * AYNI-13 subtarea 2) con Retry + CircuitBreaker (subtarea 10).
 *
 * <p><strong>El orden de las anotaciones importa, y no es el que aplica por
 * defecto.</strong> Se necesita CircuitBreaker decorando a Retry (afuera),
 * no al reves: si los reintentos internos fallan los tres, CircuitBreaker
 * debe ver UNA sola llamada fallida, no tres. Se confirmo experimentalmente
 * que el orden por defecto de resilience4j-spring-boot3 2.4.0 es el
 * contrario, asi que {@code circuit-breaker-aspect-order}/
 * {@code retry-aspect-order} se fijan explicitamente en application.yml.
 * Ver ADR-0020.
 *
 * <p><strong>El fallback se dispara para CUALQUIER excepcion</strong> que
 * propague el metodo decorado, sin importar {@code record-exceptions}/
 * {@code ignore-exceptions} (esas propiedades solo afectan las metricas del
 * circuito, no si el fallback se invoca). Por eso {@link #alAgotarLaResiliencia}
 * distingue el tipo de causa en vez de asumir que todo error implica que el
 * servicio no esta disponible.
 */
@Component
public class AdaptadorVerificadorKyc implements VerificadorKycPort {

    private final VerificationApi verificationApi;

    public AdaptadorVerificadorKyc(VerificationApi verificationApi) {
        this.verificationApi = verificationApi;
    }

    @Override
    @CircuitBreaker(name = "kycService", fallbackMethod = "alAgotarLaResiliencia")
    @Retry(name = "kycService")
    public ResultadoDeVerificacionKyc iniciar(UUID anversoDocumentoId, UUID reversoDocumentoId) {
        VerificationRequest peticion = new VerificationRequest()
                .anversoDocumentKey(anversoDocumentoId)
                .reversoDocumentKey(reversoDocumentoId);

        VerificationResponse respuesta = verificationApi.startVerification(peticion);

        return new ResultadoDeVerificacionKyc(
                respuesta.getVerificationId(), EstadoDeVerificacionKyc.PENDIENTE);
    }

    /**
     * Firma exigida por Resilience4j: mismos parametros del metodo decorado
     * mas un {@link Throwable} final.
     *
     * <p>Un {@link HttpClientErrorException} (4xx) no es un problema de
     * disponibilidad del servicio — es una peticion mal formada (ej. un UUID
     * de documento inexistente) — asi que se relanza tal cual, sin reintento
     * (ya excluido de {@code retry-exceptions}) ni conversion a "servicio no
     * disponible". Cualquier otra causa (5xx, timeout, error de conexion, o
     * {@code CallNotPermittedException} con el circuito abierto) si se
     * traduce a {@link KycServiceNoDisponibleException}.
     */
    private ResultadoDeVerificacionKyc alAgotarLaResiliencia(
            UUID anversoDocumentoId, UUID reversoDocumentoId, Throwable causa) {
        if (causa instanceof HttpClientErrorException errorDeCliente) {
            throw errorDeCliente;
        }
        throw new KycServiceNoDisponibleException(
                "kyc-service no respondio tras agotar los reintentos configurados.", causa);
    }
}
