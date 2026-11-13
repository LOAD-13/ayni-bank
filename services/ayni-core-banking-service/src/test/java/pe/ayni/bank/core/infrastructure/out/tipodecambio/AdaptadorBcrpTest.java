package pe.ayni.bank.core.infrastructure.out.tipodecambio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.core.domain.model.FuenteDeTipoDeCambioNoDisponibleException;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;

class AdaptadorBcrpTest {

    private static final String URL =
            "https://bcrp.test/api/PD04639PD-PD04640PD/json/2026-11-03/2026-11-13";

    private MockRestServiceServer bcrp;
    private AdaptadorBcrp adaptador;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://bcrp.test/api");
        bcrp = MockRestServiceServer.bindTo(builder).build();
        adaptador = new AdaptadorBcrp(builder.build(), new JsonMapper());
    }

    // Respuesta real del BCRP recortada; llega con Content-Type text/html.
    private void responde(String periodos) {
        bcrp.expect(requestTo(URL)).andRespond(withSuccess("""
                {"config":{"title":"Tipo de cambio","series":[{"name":"Compra","dec":"3"},{"name":"Venta","dec":"3"}]},
                 "periods":[%s]}""".formatted(periodos), MediaType.TEXT_HTML));
    }

    @Test
    void devuelve_el_ultimo_dia_publicado_y_salta_el_que_aun_no_tiene_dato() {
        responde("""
                {"name":"11.Nov.26","values":["3.407","3.414"]},
                {"name":"12.Nov.26","values":["3.401","3.407"]},
                {"name":"13.Nov.26","values":["n.d.","n.d."]}""");

        TipoDeCambioOficial tipo = adaptador.ultimoPublicado(LocalDate.of(2026, 11, 13));

        assertThat(tipo).isEqualTo(new TipoDeCambioOficial(LocalDate.of(2026, 11, 12),
                new BigDecimal("3.401"), new BigDecimal("3.407"), "BCRP"));
        bcrp.verify();
    }

    @Test
    void sin_ningun_dia_publicado_avisa_que_la_fuente_no_esta_disponible() {
        responde("""
                {"name":"13.Nov.26","values":["n.d.","n.d."]}""");

        assertThatThrownBy(() -> adaptador.ultimoPublicado(LocalDate.of(2026, 11, 13)))
                .isInstanceOf(FuenteDeTipoDeCambioNoDisponibleException.class)
                .hasMessageContaining("no publico");
    }

    @Test
    void un_error_del_servidor_se_traduce_a_fuente_no_disponible() {
        bcrp.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adaptador.ultimoPublicado(LocalDate.of(2026, 11, 13)))
                .isInstanceOf(FuenteDeTipoDeCambioNoDisponibleException.class)
                .hasMessageContaining("no respondio");
    }

    @Test
    void una_respuesta_que_no_es_json_se_traduce_a_fuente_no_disponible() {
        bcrp.expect(requestTo(URL)).andRespond(withSuccess("<html>mantenimiento</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> adaptador.ultimoPublicado(LocalDate.of(2026, 11, 13)))
                .isInstanceOf(FuenteDeTipoDeCambioNoDisponibleException.class)
                .hasMessageContaining("ilegible");
    }

    @Test
    void descarta_un_dia_con_venta_menor_que_la_compra() {
        responde("""
                {"name":"10.Nov.26","values":["3.415","3.421"]},
                {"name":"11.Nov.26","values":["3.500","3.400"]}""");

        assertThat(adaptador.ultimoPublicado(LocalDate.of(2026, 11, 13)).fecha())
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void entiende_los_meses_abreviados_en_castellano() {
        assertThat(AdaptadorBcrp.fecha("02.Ene.27")).isEqualTo(LocalDate.of(2027, 1, 2));
        assertThat(AdaptadorBcrp.fecha("15.Ago.26")).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(AdaptadorBcrp.fecha("30.Set.26")).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(AdaptadorBcrp.fecha("31.Dic.26")).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThatThrownBy(() -> AdaptadorBcrp.fecha("31.Xyz.26")).isInstanceOf(IllegalArgumentException.class);
    }
}
