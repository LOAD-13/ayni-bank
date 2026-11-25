package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.json.JsonMapper;

import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.CierreDiarioService;
import pe.ayni.bank.core.application.usecase.ConvertirMonedaService;
import pe.ayni.bank.core.application.usecase.OperarCuentaService;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.CuentaDuplicadaException;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SegmentoCambiario;
import pe.ayni.bank.core.domain.model.TipoDeCambioAplicado;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeConversiones;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIntereses;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;

/**
 * La cuenta en dolares (HU-12, AYNI-273) contra PostgreSQL real: convive con la de soles, no se abre dos
 * veces, y rinde intereses sin frenar el cierre del mes. Sin transaccion de prueba: cada cierre confirma
 * la suya, como en produccion.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AbrirCuentaDeAhorroService.class, OperarCuentaService.class, ConvertirMonedaService.class,
        CierreDiarioService.class, AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class,
        AdaptadorRegistroDeIdempotencia.class, AdaptadorPublicadorDeEventos.class,
        AdaptadorPistaDeAuditoria.class, AdaptadorRegistroDeConversiones.class,
        AdaptadorRegistroDeIntereses.class, JsonMapper.class,
        ConcurrenciaDeTransferenciasTest.ConfirmacionDePrueba.class,
        CuentaEnDolaresEnPostgresTest.RelojDelUltimoDiaDeOctubre.class})
class CuentaEnDolaresEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Cotizacion TIPO_3_70 = new Cotizacion(
            new TipoDeCambioAplicado(new BigDecimal("3.70"), Instant.parse("2026-10-30T14:00:00Z"), false),
            new BigDecimal("3.681592"),
            new SegmentoCambiario("ESTANDAR", new BigDecimal("0.005000"), BigDecimal.ZERO), "BCRP");

    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDelUltimoDiaDeOctubre {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private AbrirCuentaDeAhorroService abrir;
    @Autowired
    private OperarCuentaService operar;
    @Autowired
    private ConvertirMonedaService convertir;
    @Autowired
    private CierreDiarioService cierre;
    @Autowired
    private AdaptadorLibroMayor libro;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Convive con la cuenta en soles, tiene su propio numero en dolares y no se abre dos veces")
    void conviveYNoSeDuplica() {
        UUID titular = UUID.randomUUID();
        Cuenta soles = abrir.abrirPara(titular, UUID.randomUUID(), Moneda.PEN);
        UUID clave = UUID.randomUUID();

        Cuenta dolares = abrir.abrirPara(titular, clave, Moneda.USD);

        assertThat(dolares.moneda()).isEqualTo(Moneda.USD);
        assertThat(dolares.numero().moneda()).isEqualTo(Moneda.USD);
        assertThat(dolares.id()).isNotEqualTo(soles.id());
        assertThat(libro.saldoDe(dolares.id(), Moneda.USD)).isEqualTo(Dinero.de("0.00", Moneda.USD));
        // Un reintento con la misma clave devuelve la misma cuenta; otra clave, una segunda cuenta: no.
        assertThat(abrir.abrirPara(titular, clave, Moneda.USD).id()).isEqualTo(dolares.id());
        assertThatThrownBy(() -> abrir.abrirPara(titular, UUID.randomUUID(), Moneda.USD))
                .isInstanceOf(CuentaDuplicadaException.class);
    }

    @Test
    @DisplayName("Al cerrar el mes, una cuenta en dolares capitaliza sus intereses contra la cuenta tecnica en dolares")
    void capitalizaEnDolares() {
        UUID titular = UUID.randomUUID();
        abrir.abrirPara(titular, UUID.randomUUID(), Moneda.PEN);
        Cuenta dolares = abrir.abrirPara(titular, UUID.randomUUID(), Moneda.USD);
        // El deposito simulado tiene tope de S/ 2 000: dos depositos para reunir S/ 3 700.
        operar.depositarSimulado(titular, Dinero.de("1850.00", Moneda.PEN), UUID.randomUUID());
        operar.depositarSimulado(titular, Dinero.de("1850.00", Moneda.PEN), UUID.randomUUID());
        convertir.convertir(titular, Dinero.de("3700.00", Moneda.PEN), TIPO_3_70, UUID.randomUUID());
        assertThat(libro.saldoDe(dolares.id(), Moneda.USD)).isEqualTo(Dinero.de("1000.00", Moneda.USD));

        // Antes de V17 esto lanzaba IllegalStateException y frenaba el cierre de todos los clientes.
        cierre.cerrar(LocalDate.parse("2026-10-31"));

        assertThat(libro.saldoDe(dolares.id(), Moneda.USD).importe()).isGreaterThan(new BigDecimal("1000.00"));
        assertThat(libro.saldoDe(libro.cuentaDeIntereses(Moneda.USD).id(), Moneda.USD).esNegativo()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.cierre_diario WHERE fecha = DATE '2026-10-31'",
                Integer.class)).isOne();
    }
}
