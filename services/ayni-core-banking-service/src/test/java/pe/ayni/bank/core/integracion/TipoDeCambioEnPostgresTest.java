package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.domain.model.TipoDeCambioConocido;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeTipoDeCambio;

/**
 * La cache del tipo de cambio de HU-12 contra PostgreSQL real: la tabla {@code tipo_cambio} de V1,
 * su indice unico por dia y fuente, y el upsert.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdaptadorRepositorioDeTipoDeCambio.class)
class TipoDeCambioEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant LUNES = Instant.parse("2026-11-23T15:00:00Z");

    @Autowired
    private AdaptadorRepositorioDeTipoDeCambio tipos;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Sin nada guardado no hay cache ni ultimo valor conocido")
    void vacio() {
        jdbc.update("DELETE FROM core.tipo_cambio");

        assertThat(tipos.obtenidoDesde(Instant.EPOCH)).isEmpty();
        assertThat(tipos.ultimoConocido()).isEmpty();
    }

    @Test
    @DisplayName("Lo guardado se lee con su momento de obtencion y sus valores sin redondear")
    void guardaYLee() {
        jdbc.update("DELETE FROM core.tipo_cambio");
        TipoDeCambioOficial viernes = oficial("2026-11-20", "3.745000", "3.752000");

        tipos.guardar(viernes, LUNES);

        assertThat(tipos.ultimoConocido()).contains(new TipoDeCambioConocido(viernes, LUNES));
    }

    @Test
    @DisplayName("Consultar de nuevo el mismo dia actualiza el momento de obtencion y no duplica la fila")
    void upsertPorFechaYFuente() {
        jdbc.update("DELETE FROM core.tipo_cambio");
        TipoDeCambioOficial viernes = oficial("2026-11-20", "3.745000", "3.752000");
        Instant sabado = LUNES.plusSeconds(5 * 86_400);

        tipos.guardar(viernes, LUNES);
        tipos.guardar(viernes, sabado);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.tipo_cambio", Integer.class)).isOne();
        assertThat(tipos.ultimoConocido()).map(TipoDeCambioConocido::obtenidoEn).contains(sabado);
    }

    @Test
    @DisplayName("La cache del dia solo devuelve lo obtenido desde el umbral, y lo mas reciente primero")
    void cacheDelDia() {
        jdbc.update("DELETE FROM core.tipo_cambio");
        tipos.guardar(oficial("2026-11-19", "3.740000", "3.747000"), LUNES.minusSeconds(2 * 86_400));
        tipos.guardar(oficial("2026-11-20", "3.745000", "3.752000"), LUNES);

        assertThat(tipos.obtenidoDesde(LUNES.plusSeconds(1))).isEmpty();
        assertThat(tipos.obtenidoDesde(LUNES.minusSeconds(60)))
                .map(c -> c.oficial().fecha()).contains(LocalDate.parse("2026-11-20"));
    }

    @Test
    @DisplayName("El ultimo conocido es el de la fecha de publicacion mas reciente")
    void ultimoConocidoPorFecha() {
        jdbc.update("DELETE FROM core.tipo_cambio");
        tipos.guardar(oficial("2026-11-20", "3.745000", "3.752000"), LUNES);
        tipos.guardar(oficial("2026-11-19", "3.740000", "3.747000"), LUNES.plusSeconds(60));

        assertThat(tipos.ultimoConocido()).map(c -> c.oficial().fecha())
                .contains(LocalDate.parse("2026-11-20"));
    }

    private static TipoDeCambioOficial oficial(String fecha, String compra, String venta) {
        return new TipoDeCambioOficial(LocalDate.parse(fecha), new BigDecimal(compra), new BigDecimal(venta), "BCRP");
    }
}
