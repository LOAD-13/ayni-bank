package pe.ayni.bank.identity.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.sesv2.SesV2Client;

/** Una SES que no responde no puede dejar el hilo esperando sin fin (ADR-0034). */
class ConfiguracionDeCorreoTest {

    @Test
    @DisplayName("El cliente de SES tiene limite por intento y limite total")
    void clienteConTimeouts() {
        try (SesV2Client ses = new ConfiguracionDeCorreo().sesV2Client("us-east-1")) {
            var config = ses.serviceClientConfiguration().overrideConfiguration();

            assertThat(config.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
            assertThat(config.apiCallTimeout()).contains(Duration.ofSeconds(12));
        }
    }
}
