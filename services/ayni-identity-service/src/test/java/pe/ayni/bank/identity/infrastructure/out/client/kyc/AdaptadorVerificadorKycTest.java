package pe.ayni.bank.identity.infrastructure.out.client.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import pe.ayni.bank.identity.domain.model.EstadoDeVerificacionKyc;
import pe.ayni.bank.identity.domain.model.KycServiceNoDisponibleException;
import pe.ayni.bank.identity.domain.model.ResultadoDeVerificacionKyc;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.api.VerificationApi;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.VerificationRequest;
import pe.ayni.bank.identity.infrastructure.out.client.kyc.model.VerificationResponse;

/**
 * Carga solo la autoconfiguracion de AOP + Retry + CircuitBreaker (no
 * {@code @SpringBootTest}, sin datasource ni Flyway ni RabbitMQ): confirma
 * que las anotaciones {@code @CircuitBreaker}/{@code @Retry} de
 * AdaptadorVerificadorKyc estan realmente conectadas al bean real via proxy
 * AOP, no solo que la configuracion de Resilience4j en si es correcta.
 */
class AdaptadorVerificadorKycTest {

    private static final UUID ANVERSO = UUID.randomUUID();
    private static final UUID REVERSO = UUID.randomUUID();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    RetryAutoConfiguration.class))
            // Duraciones cortas a proposito: solo se prueba el COMPORTAMIENTO
            // (numero de intentos, apertura del circuito), no los tiempos de
            // produccion (esos viven en application.yml, ver ADR-0020).
            .withPropertyValues(
                    // Orden explicito: CircuitBreaker afuera, Retry adentro. Sin esto, el
                    // orden por defecto de resilience4j-spring-boot3 2.4.0 resulto ser el
                    // opuesto en la practica (confirmado experimentalmente): el fallback de
                    // CircuitBreaker absorbia la excepcion en el PRIMER intento, y Retry
                    // nunca veia el fallo para reintentar. Ver ADR-0020.
                    "resilience4j.circuitbreaker.circuit-breaker-aspect-order=1",
                    "resilience4j.retry.retry-aspect-order=2",
                    "resilience4j.retry.instances.kycService.max-attempts=3",
                    "resilience4j.retry.instances.kycService.wait-duration=1ms",
                    "resilience4j.retry.instances.kycService.retry-exceptions[0]="
                            + "org.springframework.web.client.HttpServerErrorException",
                    "resilience4j.circuitbreaker.instances.kycService.sliding-window-size=4",
                    "resilience4j.circuitbreaker.instances.kycService.minimum-number-of-calls=2",
                    "resilience4j.circuitbreaker.instances.kycService.failure-rate-threshold=50",
                    "resilience4j.circuitbreaker.instances.kycService.wait-duration-in-open-state=1m",
                    "resilience4j.circuitbreaker.instances.kycService.record-exceptions[0]="
                            + "org.springframework.web.client.HttpServerErrorException");

    @Test
    @DisplayName("reintenta 3 veces ante un error 5xx y termina en KycServiceNoDisponibleException")
    void reintentaTresVecesAnteFallosDelServidor() {
        VerificationApiFalsa fake = new VerificationApiFalsa(
                () -> { throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null); });

        contextRunner
                .withBean(VerificationApi.class, () -> fake)
                .withBean(AdaptadorVerificadorKyc.class, () -> new AdaptadorVerificadorKyc(fake))
                .run(contexto -> {
                    AdaptadorVerificadorKyc adaptador = contexto.getBean(AdaptadorVerificadorKyc.class);

                    assertThatThrownBy(() -> adaptador.iniciar(ANVERSO, REVERSO))
                            .isInstanceOf(KycServiceNoDisponibleException.class)
                            .hasCauseInstanceOf(HttpServerErrorException.class);

                    assertThat(fake.llamadas).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("un error 4xx no dispara reintento")
    void noReintentaAnteUnErrorDeCliente() {
        VerificationApiFalsa fake = new VerificationApiFalsa(
                () -> { throw HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "", null, null, null); });

        contextRunner
                .withBean(VerificationApi.class, () -> fake)
                .withBean(AdaptadorVerificadorKyc.class, () -> new AdaptadorVerificadorKyc(fake))
                .run(contexto -> {
                    AdaptadorVerificadorKyc adaptador = contexto.getBean(AdaptadorVerificadorKyc.class);

                    assertThatThrownBy(() -> adaptador.iniciar(ANVERSO, REVERSO))
                            .isInstanceOf(HttpClientErrorException.class);

                    assertThat(fake.llamadas).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("el circuito se abre tras agotar el umbral de fallos y deja de llamar al servicio")
    void elCircuitoSeAbreTrasFallosRepetidos() {
        VerificationApiFalsa fake = new VerificationApiFalsa(
                () -> { throw HttpServerErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "", null, null, null); });

        contextRunner
                .withBean(VerificationApi.class, () -> fake)
                .withBean(AdaptadorVerificadorKyc.class, () -> new AdaptadorVerificadorKyc(fake))
                .run(contexto -> {
                    AdaptadorVerificadorKyc adaptador = contexto.getBean(AdaptadorVerificadorKyc.class);

                    // 2 llamadas (minimum-number-of-calls), cada una agota sus 3
                    // reintentos: 100% de fallo, supera el 50% de umbral -> circuito ABIERTO.
                    for (int i = 0; i < 2; i++) {
                        assertThatThrownBy(() -> adaptador.iniciar(ANVERSO, REVERSO))
                                .isInstanceOf(KycServiceNoDisponibleException.class);
                    }
                    int llamadasAntesDeAbrir = fake.llamadas;

                    // Con el circuito abierto, ni siquiera se llama al servicio real:
                    // el conteo de llamadas al fake no debe aumentar.
                    assertThatThrownBy(() -> adaptador.iniciar(ANVERSO, REVERSO))
                            .isInstanceOf(KycServiceNoDisponibleException.class);
                    assertThat(fake.llamadas).isEqualTo(llamadasAntesDeAbrir);
                });
    }

    @Test
    @DisplayName("una respuesta exitosa no pasa por el fallback")
    void unaRespuestaExitosaLlegaSinPasarPorElFallback() {
        UUID verificacionId = UUID.randomUUID();
        VerificationApiFalsa fake = new VerificationApiFalsa(() ->
                new VerificationResponse()
                        .verificationId(verificacionId)
                        .status(VerificationResponse.StatusEnum.PENDING));

        contextRunner
                .withBean(VerificationApi.class, () -> fake)
                .withBean(AdaptadorVerificadorKyc.class, () -> new AdaptadorVerificadorKyc(fake))
                .run(contexto -> {
                    AdaptadorVerificadorKyc adaptador = contexto.getBean(AdaptadorVerificadorKyc.class);

                    ResultadoDeVerificacionKyc resultado = adaptador.iniciar(ANVERSO, REVERSO);

                    assertThat(resultado.verificacionId()).isEqualTo(verificacionId);
                    assertThat(resultado.estado()).isEqualTo(EstadoDeVerificacionKyc.PENDIENTE);
                    assertThat(fake.llamadas).isEqualTo(1);
                });
    }

    /** Sustituye a VerificationApi (clase concreta generada, no una interfaz). */
    private static final class VerificationApiFalsa extends VerificationApi {
        private final java.util.function.Supplier<VerificationResponse> comportamiento;
        int llamadas = 0;

        VerificationApiFalsa(java.util.function.Supplier<VerificationResponse> comportamiento) {
            this.comportamiento = comportamiento;
        }

        @Override
        public VerificationResponse startVerification(VerificationRequest verificationRequest) {
            llamadas++;
            return comportamiento.get();
        }
    }
}
