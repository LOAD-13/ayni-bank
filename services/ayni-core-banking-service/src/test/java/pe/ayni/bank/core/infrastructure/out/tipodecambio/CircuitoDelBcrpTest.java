package pe.ayni.bank.core.infrastructure.out.tipodecambio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.port.out.FuenteDeTipoDeCambioPort;

/**
 * El circuito del BCRP (HU-12, AYNI-268) con el aspecto de Resilience4j de verdad: tras las
 * fallas necesarias se abre y deja de llamar a la fuente, y lo que sale es la excepcion del dominio.
 */
class CircuitoDelBcrpTest {

    private static final MockRestServiceServer[] SERVIDOR = new MockRestServiceServer[1];

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, CircuitBreakerAutoConfiguration.class))
            .withUserConfiguration(Configuracion.class)
            .withPropertyValues(
                    "resilience4j.circuitbreaker.instances.bcrp.slidingWindowSize=2",
                    "resilience4j.circuitbreaker.instances.bcrp.minimumNumberOfCalls=2",
                    "resilience4j.circuitbreaker.instances.bcrp.failureRateThreshold=50",
                    "resilience4j.circuitbreaker.instances.bcrp.waitDurationInOpenState=60s");

    @Test
    @DisplayName("Tras las fallas el circuito se abre, no se llama mas al BCRP y sale la excepcion del dominio")
    void seAbreTrasLasFallas() {
        contexto.run(ctx -> {
            FuenteDeTipoDeCambioPort bcrp = ctx.getBean(FuenteDeTipoDeCambioPort.class);
            SERVIDOR[0].expect(times(2), anything()).andRespond(withServerError());
            LocalDate hoy = LocalDate.parse("2026-11-23");

            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> bcrp.ultimoPublicado(hoy))
                        .isInstanceOf(FuenteDeTipoDeCambioNoDisponibleException.class)
                        .hasMessage("El BCRP no respondio");
            }

            // Tercera llamada: el circuito esta abierto. Si llegara una peticion HTTP mas, el
            // servidor simulado fallaria porque solo espera dos.
            assertThatThrownBy(() -> bcrp.ultimoPublicado(hoy))
                    .isInstanceOf(FuenteDeTipoDeCambioNoDisponibleException.class)
                    .hasMessage("Circuito del BCRP abierto")
                    .hasCauseInstanceOf(CallNotPermittedException.class);
            SERVIDOR[0].verify();
            assertThat(ctx).hasNotFailed();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Configuracion {

        @Bean
        AdaptadorBcrp adaptadorBcrp() {
            RestClient.Builder builder = RestClient.builder().baseUrl("https://bcrp.test/api");
            SERVIDOR[0] = MockRestServiceServer.bindTo(builder).build();
            return new AdaptadorBcrp(builder.build(), new JsonMapper());
        }
    }
}
