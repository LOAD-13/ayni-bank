package pe.ayni.bank.core.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.domain.model.AsientoConSaldo;
import pe.ayni.bank.core.domain.model.FiltroDeMovimientos;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;

/**
 * AYNI-319 contra un PostgreSQL de verdad: el historial paginado en la base devuelve exactamente
 * lo mismo que el calculo en memoria al que sustituye —mismos asientos, mismo orden y mismo saldo
 * posterior— con cualquier combinacion de filtros y pagina. El feature flag elige entre los dos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class HistorialEnBaseTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Cuentas tecnicas que siembran V3 y V5: validas para la clave foranea. */
    private static final UUID FONDEO = UUID.fromString("f0de0000-0000-4000-8000-000000000001");
    private static final UUID INTERESES = UUID.fromString("1e7e0000-0000-4000-8000-000000000001");

    @Autowired
    private CuentaJpaRepository cuentas;

    @Autowired
    private AsientoJpaRepository asientos;

    @Autowired
    private JdbcTemplate jdbc;

    private Instant inicio;

    /**
     * 240 asientos de importe y tipo al azar cada 20 minutos (algo mas de tres dias, para que
     * los filtros de fecha recorten), y otros tantos de otra cuenta que no deben colarse. Desde
     * ahora y hacia adelante: las particiones del mes y de los siguientes ya existen.
     */
    @BeforeEach
    void sembrar() {
        jdbc.update("DELETE FROM core.asiento");
        inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Random azar = new Random(319);
        for (int i = 0; i < 240; i++) {
            for (UUID cuenta : List.of(FONDEO, INTERESES)) {
                jdbc.update("""
                        INSERT INTO core.asiento (id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto, registrado_en)
                        VALUES (?, ?, ?, ?, ?, 'PEN', 'Asiento de prueba', ?)""",
                        UUID.randomUUID(), cuenta, UUID.randomUUID(), azar.nextInt(3) == 0 ? "CARGO" : "ABONO",
                        BigDecimal.valueOf(100 + azar.nextInt(90_000), 2),
                        java.sql.Timestamp.from(inicio.plus(20L * i, ChronoUnit.MINUTES)));
            }
        }
    }

    private List<AsientoConSaldo> historial(boolean enBase, FiltroDeMovimientos filtro) {
        return new AdaptadorLibroMayor(cuentas, asientos, FONDEO, INTERESES, enBase).filtrarAsientosDe(FONDEO, filtro);
    }

    @Test
    @DisplayName("Con cualquier filtro y pagina, la base devuelve lo mismo que el calculo en memoria")
    void mismoResultadoQueEnMemoria() {
        LocalDate primerDia = inicio.atZone(ZoneOffset.UTC).toLocalDate();
        List<FiltroDeMovimientos> filtros = List.of(
                new FiltroDeMovimientos(null, null, null, null, null, 0, 20),
                new FiltroDeMovimientos(null, null, null, null, null, 3, 50),
                new FiltroDeMovimientos(null, null, null, null, null, 99, 20),
                new FiltroDeMovimientos(primerDia.plusDays(1), primerDia.plusDays(2), null, null, null, 0, 50),
                new FiltroDeMovimientos(null, null, TipoDeAsiento.CARGO, null, null, 1, 10),
                new FiltroDeMovimientos(null, null, TipoDeAsiento.ABONO, new BigDecimal("100.00"),
                        new BigDecimal("500.00"), 0, 50),
                new FiltroDeMovimientos(primerDia, primerDia.plusDays(1), TipoDeAsiento.ABONO, null,
                        new BigDecimal("300.00"), 1, 5));

        for (FiltroDeMovimientos filtro : filtros) {
            assertThat(historial(true, filtro)).as(filtro.toString()).isEqualTo(historial(false, filtro));
        }
        assertThat(historial(true, filtros.get(0))).hasSize(20)
                .allSatisfy(fila -> assertThat(fila.asiento().cuentaId()).isEqualTo(FONDEO));
        assertThat(historial(true, filtros.get(2))).isEmpty();
    }
}
