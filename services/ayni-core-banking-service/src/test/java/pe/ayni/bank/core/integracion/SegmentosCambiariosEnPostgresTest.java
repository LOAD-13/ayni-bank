package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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

import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorSegmentosCambiarios;

/** Los segmentos de V1 y el saldo promedio sacado de los devengos (HU-12), contra PostgreSQL real. */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdaptadorSegmentosCambiarios.class)
class SegmentosCambiariosEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDate DESDE = LocalDate.parse("2026-11-01");
    private static final LocalDate HASTA = LocalDate.parse("2026-11-06");

    @Autowired
    private AdaptadorSegmentosCambiarios segmentos;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Los dos segmentos del catalogo llegan con su margen y su saldo minimo")
    void catalogo() {
        assertThat(segmentos.segmentos()).extracting(SegmentoCambiario::codigo, s -> s.spread().doubleValue(),
                        s -> s.saldoPromedioMinimo().doubleValue())
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple("ESTANDAR", 0.005, 0.0),
                        org.assertj.core.api.Assertions.tuple("PREFERENCIAL", 0.0025, 5000.0));
    }

    @Test
    @DisplayName("El promedio cuenta como cero los dias sin devengo: 3 dias con S/ 6 000 en una ventana de 6 son S/ 3 000")
    void promedioConDiasSinSaldo() {
        UUID titular = UUID.randomUUID();
        UUID cuenta = abrirCuenta(titular, "00110000099901", "99900101000000099901");
        devengar(cuenta, "2026-11-02", "6000.00");
        devengar(cuenta, "2026-11-03", "6000.00");
        devengar(cuenta, "2026-11-05", "6000.00");

        assertThat(segmentos.saldoPromedioEnSoles(titular, DESDE, HASTA)).isEqualTo(new BigDecimal("3000.00"));
    }

    @Test
    @DisplayName("Solo entran los devengos del rango y del titular que pregunta")
    void soloElRangoYElTitular() {
        UUID titular = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        UUID suya = abrirCuenta(titular, "00110000099902", "99900101000000099902");
        UUID ajena = abrirCuenta(otro, "00110000099903", "99900101000000099903");
        devengar(suya, "2026-11-03", "600.00");
        devengar(suya, "2026-10-31", "9999.00");
        devengar(suya, "2026-11-07", "9999.00");
        devengar(ajena, "2026-11-03", "9999.00");

        assertThat(segmentos.saldoPromedioEnSoles(titular, DESDE, HASTA)).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Sin cuenta o sin devengos el promedio es cero")
    void sinHistorial() {
        assertThat(segmentos.saldoPromedioEnSoles(UUID.randomUUID(), DESDE, HASTA)).isEqualTo(new BigDecimal("0.00"));
    }

    private UUID abrirCuenta(UUID titular, String numero, String cci) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO core.cuenta (id, usuario_id, producto_id, numero, cci, moneda, estado)"
                + " VALUES (?, ?, 1, ?, ?, 'PEN', 'ACTIVA')", id, titular, numero, cci);
        return id;
    }

    private void devengar(UUID cuenta, String fecha, String saldo) {
        jdbc.update("INSERT INTO core.devengo_interes (id, cuenta_id, fecha, saldo_base, tasa_producto_id, tea,"
                + " importe, registrado_en) VALUES (?, ?, ?::date, ?::numeric,"
                + " (SELECT id FROM core.tasa_producto WHERE producto_id = 1 AND vigencia_hasta IS NULL),"
                + " 0.045, 0.1, now())", UUID.randomUUID(), cuenta, fecha, saldo);
    }
}
