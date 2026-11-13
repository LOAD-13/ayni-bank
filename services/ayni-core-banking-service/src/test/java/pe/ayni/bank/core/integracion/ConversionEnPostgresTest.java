package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.ConvertirMonedaService;
import pe.ayni.bank.core.application.usecase.OperarCuentaService;
import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTiempo;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeConversiones;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;

/**
 * La conversion de HU-12 contra PostgreSQL real: la migracion V13, las dos patas en el libro
 * mayor, la fila que las une y la idempotencia.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConvertirMonedaService.class, OperarCuentaService.class, AbrirCuentaDeAhorroService.class,
        AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class, AdaptadorRegistroDeIdempotencia.class,
        AdaptadorPublicadorDeEventos.class, AdaptadorPistaDeAuditoria.class, AdaptadorRegistroDeConversiones.class,
        ConfiguracionDeTiempo.class, ObjectMapper.class, ConcurrenciaDeTransferenciasTest.ConfirmacionDePrueba.class})
class ConversionEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final TipoDeCambioAplicado VENTA =
            new TipoDeCambioAplicado(new BigDecimal("3.70"), Instant.parse("2026-11-13T14:00:00Z"), false);

    @Autowired
    private ConvertirMonedaService convertir;
    @Autowired
    private OperarCuentaService operar;
    @Autowired
    private AbrirCuentaDeAhorroService abrir;
    @Autowired
    private AdaptadorLibroMayor libro;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("S/ 370.00 a 3.70: el cliente recibe US$ 100.00 y las posiciones del banco recogen cada moneda")
    void conversionCompleta() {
        UUID titular = UUID.randomUUID();
        Cuenta soles = abrir.abrirPara(titular, UUID.randomUUID(), Moneda.PEN);
        Cuenta dolares = abrir.abrirPara(titular, UUID.randomUUID(), Moneda.USD);
        operar.depositarSimulado(titular, Dinero.de("500.00", Moneda.PEN), UUID.randomUUID());

        Conversion c = convertir.convertir(titular, Dinero.de("370.00", Moneda.PEN), VENTA, UUID.randomUUID());

        assertThat(libro.saldoDe(soles.id(), Moneda.PEN)).isEqualTo(Dinero.de("130.00", Moneda.PEN));
        assertThat(libro.saldoDe(dolares.id(), Moneda.USD)).isEqualTo(Dinero.de("100.00", Moneda.USD));
        assertThat(libro.saldoDe(libro.cuentaDePosicionCambiaria(Moneda.PEN).id(), Moneda.PEN))
                .isEqualTo(Dinero.de("370.00", Moneda.PEN));
        assertThat(libro.saldoDe(libro.cuentaDePosicionCambiaria(Moneda.USD).id(), Moneda.USD))
                .isEqualTo(Dinero.de("-100.00", Moneda.USD));

        // Escenario 3: el tipo aplicado y su momento quedan junto a la operacion.
        assertThat(jdbc.queryForMap("SELECT importe_origen, importe_destino, tipo_aplicado, tipo_obtenido_en,"
                + " tipo_en_cache FROM core.conversion WHERE id = ?", c.id()))
                .containsEntry("importe_origen", new BigDecimal("370.00"))
                .containsEntry("importe_destino", new BigDecimal("100.00"))
                .containsEntry("tipo_aplicado", new BigDecimal("3.700000"))
                .containsEntry("tipo_en_cache", false);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT moneda) FROM core.asiento WHERE movimiento_id = ?",
                Integer.class, c.salida().id())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.evento_auditoria WHERE tipo = 'CONVERSION_REALIZADA'"
                + " AND usuario_id = ?", Integer.class, titular)).isOne();
    }

    @Test
    @DisplayName("Un reintento con la misma clave devuelve la misma conversion y no mueve dinero otra vez")
    void idempotente() {
        UUID titular = UUID.randomUUID();
        Cuenta soles = abrir.abrirPara(titular, UUID.randomUUID(), Moneda.PEN);
        abrir.abrirPara(titular, UUID.randomUUID(), Moneda.USD);
        operar.depositarSimulado(titular, Dinero.de("500.00", Moneda.PEN), UUID.randomUUID());
        UUID clave = UUID.randomUUID();

        Conversion primera = convertir.convertir(titular, Dinero.de("37.00", Moneda.PEN), VENTA, clave);
        Conversion segunda = convertir.convertir(titular, Dinero.de("37.00", Moneda.PEN), VENTA, clave);

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(segunda.importeDestino()).isEqualTo(Dinero.de("10.00", Moneda.USD));
        assertThat(libro.saldoDe(soles.id(), Moneda.PEN)).isEqualTo(Dinero.de("463.00", Moneda.PEN));
    }
}
