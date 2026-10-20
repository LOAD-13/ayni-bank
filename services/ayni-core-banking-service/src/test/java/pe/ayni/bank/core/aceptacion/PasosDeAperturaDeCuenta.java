package pe.ayni.bank.core.aceptacion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.Before;
import io.cucumber.java.PendingException;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.domain.model.Cci;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.CuentaDuplicadaException;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoCuenta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeMensajeria;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada.SolicitudAprobada;
import pe.ayni.bank.core.infrastructure.out.messaging.PublicadorDeOutbox;

/** Pasos de {@code apertura-automatica-de-cuenta.feature} · HU-05 · T-30. */
public class PasosDeAperturaDeCuenta {

    private static final short AHORRO_PEN = 1;

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AbrirCuentaDeAhorroUseCase abrir;
    @Autowired
    private EscuchadorDeSolicitudAprobada escuchador;
    @Autowired
    private PublicadorDeOutbox publicador;
    @Autowired
    private RepositorioDeCuentasPort cuentas;
    @Autowired
    private LibroMayorPort libro;
    @Autowired
    private RabbitTemplate rabbit;

    /** Cada nombre del escenario es un titular distinto, con su propia solicitud. */
    private final Map<String, UUID> titulares = new HashMap<>();
    private final Map<String, UUID> solicitudes = new HashMap<>();
    private RuntimeException rechazo;

    /** El broker vuelve a responder en cada escenario: el fallo lo provoca el paso que lo necesita. */
    @Before(order = 1)
    public void brokerDisponible() {
        Mockito.reset(rabbit);
    }

    private UUID titular(String nombre) {
        return titulares.computeIfAbsent(nombre, n -> UUID.randomUUID());
    }

    private Cuenta cuentaActivaDe(String nombre) {
        return cuentas.buscarActivaDe(titular(nombre), Moneda.PEN)
                .orElseThrow(() -> new AssertionError(nombre + " no tiene una cuenta activa en soles."));
    }

    /** La aprobacion entra por el mismo escuchador que en produccion recibe el evento de identity. */
    private void aprobar(String nombre) {
        UUID solicitud = solicitudes.computeIfAbsent(nombre, n -> UUID.randomUUID());
        escuchador.alAprobarseLaSolicitud(new SolicitudAprobada(solicitud, titular(nombre), "2026-10-20T15:00:00Z"));
    }

    /** {@code queryForMap} exige exactamente una fila: dos eventos de apertura tambien serian un fallo. */
    private Map<String, Object> eventoDeApertura(String nombre) {
        return jdbc.queryForMap("""
                SELECT id, publicado_en, intentos, ultimo_error, xmin::text AS transaccion FROM core.outbox
                 WHERE tipo_evento = 'CuentaAperturada' AND carga ->> 'usuarioId' = ?
                """, titular(nombre).toString());
    }

    // ─── Dado ────────────────────────────────────────────────────────────────

    @Dado("que {word} ya posee una cuenta de ahorro activa en soles")
    public void yaPoseeCuenta(String nombre) {
        abrir.abrirPara(titular(nombre), UUID.randomUUID(), Moneda.PEN);
    }

    @Dado("que la solicitud de onboarding de {word} pasa a estado APROBADA")
    public void solicitudAprobada(String nombre) {
        solicitudes.put(nombre, UUID.randomUUID());
    }

    @Dado("que la cuenta de {word} se creó correctamente pero RabbitMQ no está disponible")
    public void brokerCaido(String nombre) {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbit).send(anyString(), anyString(), any(Message.class));
        aprobar(nombre);
    }

    @Dado("que la solicitud de onboarding de {word} queda en estado RECHAZADA")
    public void solicitudRechazada(String nombre) {
        throw new PendingException("AYNI-327 · HU-05 escenario 3: identity no publica la solicitud rechazada, "
                + "asi que no hay evento que procesar ni motivo que notificar.");
    }

    // ─── Cuando ──────────────────────────────────────────────────────────────

    @Cuando("el sistema procesa la aprobación de {word}")
    public void procesaAprobacion(String nombre) {
        aprobar(nombre);
    }

    @Cuando("se intenta abrir una segunda cuenta en soles para {word}")
    public void segundaCuenta(String nombre) {
        try {
            abrir.abrirPara(titular(nombre), UUID.randomUUID(), Moneda.PEN);
        } catch (RuntimeException excepcion) {
            rechazo = excepcion;
        }
    }

    @Cuando("el publicador intenta enviar el evento CuentaAperturada")
    public void publicadorIntenta() {
        publicador.publicarLoPendiente();
    }

    @Cuando("el sistema procesa el rechazo de {word}")
    public void procesaRechazo(String nombre) {
        throw new PendingException("AYNI-327 · HU-05 escenario 3: no existe el evento de solicitud rechazada.");
    }

    // ─── Entonces ────────────────────────────────────────────────────────────

    @Entonces("{word} tiene una cuenta de ahorro en soles con saldo S\\/ {word} y estado {word}")
    public void tieneCuenta(String nombre, String saldo, String estado) {
        Cuenta cuenta = cuentaActivaDe(nombre);
        assertThat(cuenta.moneda()).isEqualTo(Moneda.PEN);
        assertThat(cuenta.estado().name()).isEqualTo(estado);
        assertThat(libro.saldoDe(cuenta.id(), Moneda.PEN)).isEqualTo(Dinero.de(saldo, Moneda.PEN));
    }

    @Y("la cuenta de {word} está asociada al producto de ahorro en soles con su TREA vigente")
    public void producto(String nombre) {
        Cuenta cuenta = cuentaActivaDe(nombre);
        assertThat(cuenta.productoId()).isEqualTo(AHORRO_PEN);
        assertThat(cuentas.treaVigenteDe(cuenta.productoId())).isPresent();
    }

    @Y("el número de cuenta de {word} es único y su CCI tiene 20 dígitos con control válido")
    public void numeroYCci(String nombre) {
        Cuenta cuenta = cuentaActivaDe(nombre);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.cuenta WHERE numero = ?", Integer.class,
                cuenta.numero().valor())).isEqualTo(1);
        String cci = jdbc.queryForObject("SELECT cci FROM core.cuenta WHERE id = ?", String.class, cuenta.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.cuenta WHERE cci = ?", Integer.class, cci))
                .isEqualTo(1);
        assertThat(cci).matches("\\d{20}");
        assertThatCode(() -> new Cci(cci)).doesNotThrowAnyException();
    }

    @Y("el evento CuentaAperturada de {word} está en la bandeja de salida, escrito en el mismo COMMIT que su cuenta")
    public void eventoEnLaBandeja(String nombre) {
        Cuenta cuenta = cuentaActivaDe(nombre);
        Map<String, Object> evento = eventoDeApertura(nombre);
        assertThat(evento.get("publicado_en")).isNull();
        assertThat(jdbc.queryForObject("SELECT carga ->> 'cuentaId' FROM core.outbox WHERE id = ?", String.class,
                evento.get("id"))).isEqualTo(cuenta.id().toString());
        // xmin es el identificador de la transaccion que escribio la fila. Si coincide, la
        // cuenta y su evento se confirmaron en el mismo COMMIT: no puede existir uno sin otro.
        String transaccionDeLaCuenta = jdbc.queryForObject("SELECT xmin::text FROM core.cuenta WHERE id = ?",
                String.class, cuenta.id());
        assertThat(evento.get("transaccion")).isEqualTo(transaccionDeLaCuenta);
    }

    @Entonces("el sistema rechaza la apertura")
    public void rechaza() {
        assertThat(rechazo).isInstanceOf(CuentaDuplicadaException.class);
    }

    @Y("{word} mantiene una única cuenta activa en soles")
    public void unicaCuenta(String nombre) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM core.cuenta WHERE usuario_id = ? AND moneda = 'PEN' AND estado = 'ACTIVA'
                """, Integer.class, titular(nombre))).isEqualTo(1);
        eventoDeApertura(nombre);
    }

    @Entonces("el intento duplicado de {word} queda registrado en la pista de auditoría")
    public void auditoriaDelDuplicado(String nombre) {
        throw new PendingException("AYNI-326 · HU-05 escenario 2: AbrirCuentaDeAhorroService rechaza el duplicado "
                + "sin escribir el intento en core.evento_auditoria.");
    }

    @Entonces("el sistema no crea ninguna cuenta para {word}")
    public void sinCuenta(String nombre) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM core.cuenta WHERE usuario_id = ?", Integer.class,
                titular(nombre))).isZero();
    }

    @Y("el sistema notifica a {word} el motivo del rechazo")
    public void notificaRechazo(String nombre) {
        throw new PendingException("AYNI-327 · HU-05 escenario 3: nadie envia la plantilla KYC_RECHAZADO de notification.");
    }

    @Entonces("la cuenta de {word} permanece creada y consistente")
    public void cuentaConsistente(String nombre) {
        Cuenta cuenta = cuentaActivaDe(nombre);
        assertThat(cuenta.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(cuentas.asientosDe(cuenta.id())).isEmpty();
        assertThat(libro.saldoDe(cuenta.id(), Moneda.PEN)).isEqualTo(Dinero.de("0.00", Moneda.PEN));
    }

    @Y("el evento CuentaAperturada de {word} permanece pendiente en la bandeja de salida")
    public void eventoPendiente(String nombre) {
        Map<String, Object> evento = eventoDeApertura(nombre);
        assertThat(evento.get("publicado_en")).isNull();
        assertThat(((Number) evento.get("intentos")).intValue()).isEqualTo(1);
        assertThat((String) evento.get("ultimo_error")).isNotBlank();
    }

    @Y("el evento CuentaAperturada de {word} se publica en cuanto el broker se restablece")
    public void sePublica(String nombre) {
        Mockito.reset(rabbit);
        // En produccion el publicador corre solo cada dos segundos (@Scheduled); aqui se
        // dispara a mano la siguiente pasada, la primera con el broker de vuelta.
        publicador.publicarLoPendiente();

        ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(ConfiguracionDeMensajeria.EXCHANGE), eq("core.cuenta.aperturada"), mensaje.capture());
        assertThat(mensaje.getValue().getMessageProperties().getType()).isEqualTo("CuentaAperturada");
        assertThat(new String(mensaje.getValue().getBody(), UTF_8)).contains(titular(nombre).toString());
        assertThat(eventoDeApertura(nombre).get("publicado_en")).isNotNull();
    }
}
