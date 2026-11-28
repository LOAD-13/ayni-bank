package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.cucumber.java.After;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.aceptacion.ContextoDeAceptacion.RelojDePrueba;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.HistorialDeCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.port.in.BloquearCuentaUseCase;
import pe.ayni.bank.core.domain.port.in.ConsultarHistorialDeCuentaUseCase;
import pe.ayni.bank.core.domain.port.in.ControlarTarjetaUseCase;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.PistaDeAuditoriaPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada.SolicitudAprobada;
import pe.ayni.bank.core.infrastructure.in.web.BloqueosController;

/** Pasos de {@code pista-de-auditoria.feature} · HU-15 · AYNI-285. */
public class PasosDePistaDeAuditoria {

    private static final Instant AHORA = Instant.parse("2026-11-27T15:00:00Z");

    @Autowired
    private EscuchadorDeSolicitudAprobada escuchador;
    @Autowired
    private OperarCuentaUseCase operar;
    @Autowired
    private ControlarTarjetaUseCase controles;
    @Autowired
    private BloquearCuentaUseCase bloqueos;
    @Autowired
    private ConsultarHistorialDeCuentaUseCase historial;
    @Autowired
    private PistaDeAuditoriaPort auditoria;
    @Autowired
    private RepositorioDeTarjetasPort tarjetas;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID teresa;
    private String numeroDeCuenta;
    private Map<String, Object> registro;
    private List<RuntimeException> intentos;
    private Instant antesDelBloqueo;
    private UUID supervisor;
    private HistorialDeCuenta consulta;
    private UUID oscar;
    private RuntimeException denegacion;
    private List<ILoggingEvent> log;
    private UUID rocio;

    @After
    public void sinPeticion() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Dado("que Teresa es clienta con una cuenta de ahorro con S\\/ {word} y su tarjeta de débito virtual")
    public void teresaConCuentaYTarjeta(String saldo) {
        reloj.fijar(AHORA);
        teresa = UUID.randomUUID();
        escuchador.alAprobarseLaSolicitud(new SolicitudAprobada(UUID.randomUUID(), teresa, AHORA.toString()));
        numeroDeCuenta = jdbc.queryForObject("SELECT numero FROM core.cuenta WHERE usuario_id = ?", String.class, teresa);
        operar.depositarSimulado(teresa, Dinero.de(saldo, Moneda.PEN), UUID.randomUUID());
    }

    // ── Escenario 1 ────────────────────────────────────────────────────────

    @Dado("que existe en la pista el registro del depósito de Teresa")
    public void existeElRegistro() {
        registro = jdbc.queryForMap("SELECT * FROM core.evento_auditoria WHERE usuario_id = ? AND tipo = 'DEPOSITO_SIMULADO'",
                teresa);
    }

    @Cuando("se intenta modificar y borrar ese registro con el usuario de la aplicación")
    public void seIntentaAlterar() {
        Object id = registro.get("id");
        intentos = List.of(
                catchRuntimeException(() -> jdbc.update("UPDATE core.evento_auditoria SET tipo = 'OPERACION_REPETIDA'"
                        + " WHERE id = ?", id)),
                catchRuntimeException(() -> jdbc.update("DELETE FROM core.evento_auditoria WHERE id = ?", id)));
    }

    @Entonces("la base de datos rechaza las dos operaciones por falta de permisos")
    public void rechazaPorPermisos() {
        assertThat(intentos).allSatisfy(e -> assertThat(
                NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql ? sql.getSQLState() : null)
                .isEqualTo("42501"));
    }

    @Y("el registro original permanece intacto")
    public void registroIntacto() {
        assertThat(jdbc.queryForMap("SELECT * FROM core.evento_auditoria WHERE id = ?", registro.get("id")))
                .isEqualTo(registro);
    }

    // ── Escenario 2 ────────────────────────────────────────────────────────

    @Dado("que un operador pidió bloquear la cuenta de Teresa y un supervisor distinto lo aprobó")
    public void bloqueoConCuatroOjos() {
        UUID operador = UUID.randomUUID();
        supervisor = UUID.randomUUID();
        UUID solicitud = bloqueos.iniciar(operador, numeroDeCuenta, "Reclamo por fraude").id();
        antesDelBloqueo = Instant.now();
        // Que la aprobacion caiga en un milisegundo posterior a la fecha consultada.
        while (!Instant.now().isAfter(antesDelBloqueo.plusMillis(1))) {
            Thread.onSpinWait();
        }
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Ayni-Usuario", supervisor.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
        bloqueos.aprobar(supervisor, solicitud);
    }

    @Cuando("el auditor consulta el estado de la cuenta de Teresa en una fecha anterior al bloqueo")
    public void consultaEnFechaPasada() {
        consulta = historial.consultar(numeroDeCuenta, antesDelBloqueo);
    }

    @Entonces("el sistema devuelve la versión que tenía entonces, ACTIVA")
    public void versionDeEntonces() {
        assertThat(consulta.vigente()).hasValueSatisfying(v -> assertThat(v.estado()).isEqualTo(EstadoCuenta.ACTIVA));
    }

    @Y("al consultarla ahora devuelve la versión BLOQUEADA con el supervisor como actor del cambio")
    public void versionDeAhora() {
        assertThat(historial.consultar(numeroDeCuenta, Instant.now()).vigente()).hasValueSatisfying(v -> {
            assertThat(v.estado()).isEqualTo(EstadoCuenta.BLOQUEADA);
            assertThat(v.actor()).isEqualTo(supervisor);
        });
    }

    // ── Escenario 3 ────────────────────────────────────────────────────────

    @Dado("que Óscar ha iniciado sesión con el rol AUDITOR")
    public void oscarAuditor() {
        oscar = UUID.randomUUID();
    }

    @Cuando("Óscar intenta pedir el bloqueo de la cuenta de Teresa")
    public void oscarIntentaEscribir() {
        // Los permisos que el gateway le reenvia a un AUDITOR (catalogo de identity, V1).
        denegacion = catchRuntimeException(() -> new BloqueosController(bloqueos, auditoria).iniciar(oscar,
                "cuenta:leer_todas,cliente:leer,auditoria:leer",
                new BloqueosController.PeticionDeBloqueo(numeroDeCuenta, "Prueba")));
    }

    @Entonces("el sistema deniega la operación")
    public void deniega() {
        assertThat(denegacion).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.solicitud_bloqueo WHERE iniciada_por = ?",
                Integer.class, oscar)).isZero();
    }

    @Y("el intento de Óscar queda registrado en la propia pista de auditoría")
    public void intentoRegistrado() {
        assertThat(jdbc.queryForMap("SELECT tipo, motivo, resultado FROM core.evento_auditoria WHERE usuario_id = ?",
                oscar))
                .containsEntry("tipo", "ESCRITURA_DENEGADA")
                .containsEntry("motivo", "cliente:bloquear_iniciar")
                .containsEntry("resultado", "RECHAZADO");
    }

    // ── Escenario 4 ────────────────────────────────────────────────────────

    @Cuando("a Rocío se le abre la cuenta con su tarjeta, deposita y congela la tarjeta mientras se captura el log técnico")
    public void operaConLaTarjeta() {
        ch.qos.logback.classic.Logger raiz = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ROOT");
        ListAppender<ILoggingEvent> capturado = new ListAppender<>();
        capturado.start();
        raiz.addAppender(capturado);
        try {
            rocio = UUID.randomUUID();
            // La apertura emite tambien la tarjeta (HU-09): el PAN se genera dentro de lo que se captura.
            escuchador.alAprobarseLaSolicitud(new SolicitudAprobada(UUID.randomUUID(), rocio, AHORA.toString()));
            operar.depositarSimulado(rocio, Dinero.de("100.00", Moneda.PEN), UUID.randomUUID());
            controles.congelar(rocio);
        } finally {
            raiz.detachAppender(capturado);
        }
        log = capturado.list;
    }

    @Entonces("ninguna línea del log contiene el número completo de la tarjeta de Rocío")
    public void sinPanEnElLog() {
        String pan = panDeTeresa();
        assertThat(log).isNotEmpty();
        assertThat(log).allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(pan));
    }

    @Y("si una línea llegara a incluirlo, el patrón de los logs lo muestra enmascarado")
    public void patronEnmascara() throws Exception {
        String pan = panDeTeresa();
        String patron = (String) new YamlPropertySourceLoader().load("app", new ClassPathResource("application.yml"))
                .get(0).getProperty("logging.pattern.console");
        LoggerContext contexto = new LoggerContext();
        PatternLayout disposicion = new PatternLayout();
        disposicion.setContext(contexto);
        disposicion.getInstanceConverterMap().put("wEx", WhitespaceThrowableProxyConverter::new);
        disposicion.setPattern(patron);
        disposicion.start();
        String linea = disposicion.doLayout(new LoggingEvent("prueba", contexto.getLogger("prueba"), Level.WARN,
                "Consumo rechazado con la tarjeta " + pan, null, null));
        assertThat(linea).doesNotContain(pan).contains(pan.substring(0, 4) + "****" + pan.substring(pan.length() - 4));
    }

    private String panDeTeresa() {
        return tarjetas.deTitular(rocio).orElseThrow().pan().valor();
    }
}
