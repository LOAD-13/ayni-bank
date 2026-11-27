package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.BloquearCuentaService;
import pe.ayni.bank.core.domain.model.CuatroOjosException;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueo;
import pe.ayni.bank.core.domain.model.SolicitudDeBloqueoNoValidaException;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTiempo;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorSolicitudesDeBloqueo;

import tools.jackson.databind.json.JsonMapper;

/**
 * HU-15, AYNI-282: el bloqueo de cuenta con cuatro ojos contra PostgreSQL real. Sin transaccion de
 * la prueba, para que la denegacion se vea confirmada en su transaccion propia.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({BloquearCuentaService.class, AbrirCuentaDeAhorroService.class, AdaptadorSolicitudesDeBloqueo.class,
        AdaptadorLibroMayor.class, AdaptadorRepositorioDeCuentas.class, AdaptadorPistaDeAuditoria.class,
        AdaptadorRegistroDeIdempotencia.class, AdaptadorPublicadorDeEventos.class, ConfiguracionDeTiempo.class,
        JsonMapper.class})
class CuatroOjosEnPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID OPERADOR = UUID.randomUUID();
    private static final UUID SUPERVISOR = UUID.randomUUID();

    @Autowired
    private BloquearCuentaService bloqueos;
    @Autowired
    private AbrirCuentaDeAhorroService abrir;
    @Autowired
    private JdbcTemplate jdbc;

    private Cuenta cuentaDeCliente() {
        return abrir.abrirPara(UUID.randomUUID(), UUID.randomUUID(), Moneda.PEN);
    }

    private String estadoDe(Cuenta cuenta) {
        return jdbc.queryForObject("SELECT estado FROM core.cuenta WHERE id = ?", String.class, cuenta.id());
    }

    private int eventos(String tipo, UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM core.evento_auditoria WHERE tipo = ? AND usuario_id = ?",
                Integer.class, tipo, usuario);
    }

    @Test
    @DisplayName("Operador pide, supervisor aprueba: la cuenta queda bloqueada y cada paso en la pista")
    void flujoCompleto() {
        Cuenta cuenta = cuentaDeCliente();

        SolicitudDeBloqueo pedida = bloqueos.iniciar(OPERADOR, cuenta.numero().valor(), "Fraude reportado");
        assertThat(estadoDe(cuenta)).isEqualTo("ACTIVA");
        assertThat(bloqueos.pendientes()).extracting(SolicitudDeBloqueo::id).contains(pedida.id());

        bloqueos.aprobar(SUPERVISOR, pedida.id());

        assertThat(estadoDe(cuenta)).isEqualTo("BLOQUEADA");
        assertThat(bloqueos.pendientes()).extracting(SolicitudDeBloqueo::id).doesNotContain(pedida.id());
        assertThat(eventos("BLOQUEO_SOLICITADO", OPERADOR)).isPositive();
        assertThat(eventos("CUENTA_BLOQUEADA", SUPERVISOR)).isPositive();
    }

    @Test
    @DisplayName("Quien pidio el bloqueo no puede aprobarlo: la cuenta sigue activa y la denegacion queda escrita")
    void cuatroOjos() {
        Cuenta cuenta = cuentaDeCliente();
        UUID operador = UUID.randomUUID();
        SolicitudDeBloqueo pedida = bloqueos.iniciar(operador, cuenta.numero().valor(), "Fraude");

        assertThatThrownBy(() -> bloqueos.aprobar(operador, pedida.id())).isInstanceOf(CuatroOjosException.class);

        assertThat(estadoDe(cuenta)).isEqualTo("ACTIVA");
        assertThat(eventos("CUATRO_OJOS_DENEGADO", operador)).isOne();
        assertThat(jdbc.queryForObject("SELECT resultado FROM core.evento_auditoria WHERE tipo = 'CUATRO_OJOS_DENEGADO'"
                + " AND usuario_id = ?", String.class, operador)).isEqualTo("RECHAZADO");
    }

    @Test
    @DisplayName("La base tambien lo impide: ni el mismo aprobador ni dos pendientes por cuenta ni dos aprobaciones")
    void defensaEnLaBase() {
        Cuenta cuenta = cuentaDeCliente();
        UUID operador = UUID.randomUUID();
        SolicitudDeBloqueo pedida = bloqueos.iniciar(operador, cuenta.numero().valor(), "Fraude");

        assertThatThrownBy(() -> jdbc.update("UPDATE core.solicitud_bloqueo SET aprobada_por = ?, aprobada_en = now()"
                + " WHERE id = ?", operador, pedida.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO core.solicitud_bloqueo (id, cuenta_id, motivo, iniciada_por,"
                + " iniciada_en) VALUES (?, ?, 'otra', ?, now())", UUID.randomUUID(), cuenta.id(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        bloqueos.aprobar(SUPERVISOR, pedida.id());
        assertThatThrownBy(() -> bloqueos.aprobar(UUID.randomUUID(), pedida.id()))
                .isInstanceOf(SolicitudDeBloqueoNoValidaException.class);
    }
}
