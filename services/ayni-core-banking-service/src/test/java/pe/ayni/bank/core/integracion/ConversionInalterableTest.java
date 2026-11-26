package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.ConvertirMonedaService;
import pe.ayni.bank.core.application.usecase.OperarCuentaService;
import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTiempo;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeConversiones;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;

/**
 * La conversion es de solo insercion (HU-12, AYNI-272): nadie cambia el tipo con que se convirtio.
 * Sin transaccion de prueba: cada sentencia rechazada debe fallar por si sola, con "permiso denegado".
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ConvertirMonedaService.class, OperarCuentaService.class, AbrirCuentaDeAhorroService.class,
        AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class, AdaptadorRegistroDeIdempotencia.class,
        AdaptadorPublicadorDeEventos.class, AdaptadorPistaDeAuditoria.class, AdaptadorRegistroDeConversiones.class,
        ConfiguracionDeTiempo.class, JsonMapper.class, ConcurrenciaDeTransferenciasTest.ConfirmacionDePrueba.class})
class ConversionInalterableTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Cotizacion VENTA = new Cotizacion(
            new TipoDeCambioAplicado(new BigDecimal("3.70"), Instant.parse("2026-11-13T14:00:00Z"), false),
            new BigDecimal("3.681592"),
            new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005000"), BigDecimal.ZERO), "BCRP");

    @Autowired
    private ConvertirMonedaService convertir;
    @Autowired
    private OperarCuentaService operar;
    @Autowired
    private AbrirCuentaDeAhorroService abrir;
    @Autowired
    private JdbcTemplate jdbc;

    private static String estadoSql(DataAccessException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql ? sql.getSQLState() : null;
    }

    @Test
    @DisplayName("UPDATE, DELETE y TRUNCATE se rechazan por falta de permisos y la conversion sigue intacta")
    void soloInsercion() {
        UUID titular = UUID.randomUUID();
        abrir.abrirPara(titular, UUID.randomUUID(), Moneda.PEN);
        abrir.abrirPara(titular, UUID.randomUUID(), Moneda.USD);
        operar.depositarSimulado(titular, Dinero.de("500.00", Moneda.PEN), UUID.randomUUID());
        Conversion c = convertir.convertir(titular, Dinero.de("37.00", Moneda.PEN), VENTA, UUID.randomUUID());
        Map<String, Object> original = jdbc.queryForMap("SELECT * FROM core.conversion WHERE id = ?", c.id());

        assertThatThrownBy(() -> jdbc.update("UPDATE core.conversion SET tipo_aplicado = 1 WHERE id = ?", c.id()))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("UPDATE core.conversion SET spread = 0 WHERE id = ?", c.id()))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core.conversion WHERE id = ?", c.id()))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE core.conversion"))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));

        assertThat(jdbc.queryForMap("SELECT * FROM core.conversion WHERE id = ?", c.id())).isEqualTo(original);
    }
}
