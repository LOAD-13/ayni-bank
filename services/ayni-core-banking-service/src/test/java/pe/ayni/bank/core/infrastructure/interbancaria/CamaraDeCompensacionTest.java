package pe.ayni.bank.core.infrastructure.interbancaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import pe.ayni.bank.core.infrastructure.interbancaria.CamaraSimuladaController.Envio;

/**
 * Los dos lados del contrato con la camara de compensacion (HU-11): el simulador que hace de
 * sistema externo y el adaptador que lo llama por HTTP.
 */
class CamaraDeCompensacionTest {

    private static final String URL = "http://camara.test/simulador/camara/v1";
    private final CamaraSimuladaController simulador = new CamaraSimuladaController();

    @Test
    @DisplayName("El simulador decide por la entidad del CCI: 097 rechaza, 098 no decide, el resto acepta")
    void decidePorEntidad() {
        assertThat(simulador.enviar(new Envio(UUID.randomUUID(), "00219300001234567886", "10.00"), null).estado())
                .isEqualTo(EstadoInterbancario.COMPLETADA);
        assertThat(simulador.enviar(new Envio(UUID.randomUUID(), "09700100000000055535", "10.00"), null).estado())
                .isEqualTo(EstadoInterbancario.RECHAZADA);
        assertThat(simulador.enviar(new Envio(UUID.randomUUID(), "09800100000000077767", "10.00"), null).estado())
                .isEqualTo(EstadoInterbancario.PENDIENTE);
    }

    @Test
    @DisplayName("El simulador recuerda su decision: reenviar o consultar devuelve la misma")
    void recuerdaLaDecision() {
        UUID id = UUID.randomUUID();
        simulador.enviar(new Envio(id, "00219300001234567886", "10.00"), "RECHAZADA");

        assertThat(simulador.enviar(new Envio(id, "00219300001234567886", "10.00"), "COMPLETADA").estado())
                .isEqualTo(EstadoInterbancario.RECHAZADA);
        assertThat(simulador.consultar(id).estado()).isEqualTo(EstadoInterbancario.RECHAZADA);
    }

    @Test
    @DisplayName("El simulador valida la instruccion como lo haria la camara: 400 o 404")
    void validaLaInstruccion() {
        assertThatThrownBy(() -> simulador.enviar(new Envio(UUID.randomUUID(), "123", "10.00"), null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> simulador.enviar(new Envio(UUID.randomUUID(), "00219300001234567886", "0"), null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> simulador.enviar(new Envio(UUID.randomUUID(), "00219300001234567886", "abc"), null))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> simulador.enviar(new Envio(UUID.randomUUID(), "00219300001234567886", "1"), "X"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> simulador.consultar(UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("El adaptador envia la instruccion segun el contrato y lee el estado de la respuesta")
    void adaptadorEnviaYConsulta() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer camara = MockRestServiceServer.bindTo(builder).build();
        UUID id = UUID.randomUUID();
        camara.expect(requestTo(URL + "/transferencias")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"id\":\"" + id + "\",\"cci\":\"00219300001234567886\",\"importe\":\"10.00\"}"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"estado\":\"COMPLETADA\"}", MediaType.APPLICATION_JSON));
        camara.expect(requestTo(URL + "/transferencias/" + id)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"estado\":\"RECHAZADA\"}", MediaType.APPLICATION_JSON));

        CamaraHttpAdapter adaptador = new CamaraHttpAdapter(URL, builder);

        assertThat(adaptador.enviar(id, "00219300001234567886", "10.00")).isEqualTo(EstadoInterbancario.COMPLETADA);
        assertThat(adaptador.consultar(id)).isEqualTo(EstadoInterbancario.RECHAZADA);
        camara.verify();
    }

    @Test
    @DisplayName("Una respuesta sin estado no se toma por buena: el resultado sigue desconocido")
    void respuestaSinEstado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer camara = MockRestServiceServer.bindTo(builder).build();
        UUID id = UUID.randomUUID();
        camara.expect(requestTo(URL + "/transferencias")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        camara.expect(requestTo(URL + "/transferencias/" + id)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        CamaraHttpAdapter adaptador = new CamaraHttpAdapter(URL, builder);

        assertThatThrownBy(() -> adaptador.enviar(id, "00219300001234567886", "10.00"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adaptador.consultar(id)).isInstanceOf(IllegalStateException.class);
    }
}
