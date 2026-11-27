package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.ConsultarHistorialDeCuentaService;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.HistorialDeCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.VersionDeCuenta;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTiempo;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorHistorialDeCuentas;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;

import tools.jackson.databind.json.JsonMapper;

/**
 * HU-15, escenario 2 (AYNI-280) contra PostgreSQL real: una cuenta abierta y luego bloqueada se
 * consulta en una fecha intermedia y ahora, con el actor de cada cambio. Sin transaccion de la
 * prueba: Envers escribe las versiones al confirmar.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ConsultarHistorialDeCuentaService.class, AdaptadorHistorialDeCuentas.class, AbrirCuentaDeAhorroService.class,
        AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class, AdaptadorPistaDeAuditoria.class,
        AdaptadorRegistroDeIdempotencia.class, AdaptadorPublicadorDeEventos.class, ConfiguracionDeTiempo.class,
        JsonMapper.class})
class HistorialDeCuentaEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ConsultarHistorialDeCuentaService historial;
    @Autowired
    private AbrirCuentaDeAhorroService abrir;
    @Autowired
    private AdaptadorRepositorioDeCuentas cuentas;

    @AfterEach
    void sinPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Devuelve la version de cada fecha y el actor que hizo cada cambio")
    void estadoEnCadaFecha() {
        Cuenta cuenta = abrir.abrirPara(UUID.randomUUID(), UUID.randomUUID(), Moneda.PEN);
        Instant trasAbrir = Instant.now();
        while (!Instant.now().isAfter(trasAbrir.plusMillis(1))) {
            Thread.onSpinWait();
        }
        UUID supervisor = UUID.randomUUID();
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Ayni-Usuario", supervisor.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
        cuentas.guardar(cuenta.bloquear());

        HistorialDeCuenta entonces = historial.consultar(cuenta.numero().valor(), trasAbrir);
        HistorialDeCuenta ahora = historial.consultar(cuenta.numero().valor(), Instant.now());

        assertThat(entonces.vigente()).hasValueSatisfying(v -> assertThat(v.estado()).isEqualTo(EstadoCuenta.ACTIVA));
        assertThat(ahora.vigente()).hasValueSatisfying(v -> {
            assertThat(v.estado()).isEqualTo(EstadoCuenta.BLOQUEADA);
            assertThat(v.actor()).isEqualTo(supervisor);
            assertThat(v.tipo()).isEqualTo(VersionDeCuenta.Tipo.CAMBIO);
        });
        assertThat(ahora.versiones()).extracting(VersionDeCuenta::tipo)
                .containsExactly(VersionDeCuenta.Tipo.ALTA, VersionDeCuenta.Tipo.CAMBIO);
    }

    @Test
    @DisplayName("Una cuenta que no existe, o una cuenta tecnica, no tiene historial que mostrar")
    void cuentaInexistente() {
        assertThatThrownBy(() -> historial.consultar("00110000009999", Instant.now()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> historial.consultar("no-es-numero", Instant.now()))
                .isInstanceOf(NoSuchElementException.class);
    }
}
