package pe.ayni.bank.core.infrastructure.out.tipodecambio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;

/**
 * Contra la API publica real del BCRP. No corre en el CI, que no debe depender de un servicio
 * externo: se lanza a mano con {@code AYNI_BCRP_REAL=1} para comprobar que la URL, las series y el
 * formato de la respuesta siguen siendo los que espera el adaptador.
 */
@EnabledIfEnvironmentVariable(named = "AYNI_BCRP_REAL", matches = "1")
class AdaptadorBcrpRealTest {

    @Test
    void trae_el_ultimo_tipo_de_cambio_publicado() {
        AdaptadorBcrp adaptador = new AdaptadorBcrp("https://estadisticas.bcrp.gob.pe/estadisticas/series/api",
                Duration.ofSeconds(5), Duration.ofSeconds(15), RestClient.builder(), new JsonMapper());
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Lima"));

        TipoDeCambioOficial tipo = adaptador.ultimoPublicado(hoy);

        assertThat(tipo.fuente()).isEqualTo("BCRP");
        assertThat(tipo.fecha()).isBetween(hoy.minusDays(10), hoy);
        assertThat(tipo.compra()).isBetween(new BigDecimal("2"), new BigDecimal("6"));
        assertThat(tipo.venta()).isGreaterThanOrEqualTo(tipo.compra());
    }
}
