package pe.ayni.bank.core.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;

class ConfiguracionDeLaDocumentacionTest {

    @Test
    @DisplayName("El contrato se presenta con su nombre y apunta al gateway, no a la red interna")
    void apuntaAlGateway() {
        OpenAPI contrato = new ConfiguracionDeLaDocumentacion().contrato();

        assertThat(contrato.getInfo().getTitle()).isEqualTo("Ayni Core Banking Service");
        assertThat(contrato.getServers()).singleElement().satisfies(s -> assertThat(s.getUrl()).isEqualTo("/"));
    }
}
