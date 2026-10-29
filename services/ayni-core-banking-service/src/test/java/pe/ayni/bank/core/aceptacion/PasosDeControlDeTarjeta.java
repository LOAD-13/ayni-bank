package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.aceptacion.ContextoDeAceptacion.RelojDePrueba;
import pe.ayni.bank.core.domain.model.ControlesDeTarjeta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.port.in.ControlarTarjetaUseCase;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada.SolicitudAprobada;

/** Pasos de {@code control-de-tarjeta.feature} · HU-10 · AYNI-253. */
public class PasosDeControlDeTarjeta {

    private static final Instant AHORA = Instant.parse("2026-10-28T16:00:00Z");

    @Autowired
    private EscuchadorDeSolicitudAprobada escuchador;
    @Autowired
    private OperarCuentaUseCase operar;
    @Autowired
    private ControlarTarjetaUseCase controles;
    @Autowired
    private LibroMayorPort libro;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID lucia;
    private UUID cuentaId;
    private ControlesDeTarjeta estadoControles;
    private RuntimeException errorConsumo;

    @Dado("que Lucía tiene una cuenta de ahorro con S\\/ {word} y su tarjeta de débito virtual ACTIVA")
    public void luciaConCuentaYTarjeta(String saldoInicial) {
        reloj.fijar(AHORA);
        lucia = UUID.randomUUID();
        escuchador.alAprobarseLaSolicitud(new SolicitudAprobada(UUID.randomUUID(), lucia, AHORA.toString()));
        cuentaId = jdbc.queryForObject("SELECT id FROM core.cuenta WHERE usuario_id = ?", UUID.class, lucia);
        operar.depositarSimulado(lucia, Dinero.de(saldoInicial, Moneda.PEN), UUID.randomUUID());
        estadoControles = controles.consultarControles(lucia);
        assertThat(estadoControles.estado()).isEqualTo(EstadoDeTarjeta.ACTIVA);
    }

    @Dado("que Lucía configuró un límite diario de consumo de S\\/ {word} en su tarjeta")
    public void configuroLimiteDiario(String limite) {
        estadoControles = controles.configurar(lucia, Dinero.de(limite, Moneda.PEN), true);
        assertThat(estadoControles.limiteDiario()).isEqualTo(Dinero.de(limite, Moneda.PEN));
    }

    @Y("que Lucía ya realizó un consumo con tarjeta de S\\/ {word} hoy")
    public void yaRealizoUnConsumoHoy(String importe) {
        controles.consumir(lucia, Dinero.de(importe, Moneda.PEN), false, "Supermercado");
    }

    @Cuando("Lucía congela su tarjeta desde la aplicación")
    public void congelaSuTarjeta() {
        estadoControles = controles.congelar(lucia);
    }

    @Cuando("Lucía descongela su tarjeta")
    public void descongelaSuTarjeta() {
        estadoControles = controles.descongelar(lucia);
    }

    @Cuando("Lucía intenta realizar otro consumo con tarjeta de S\\/ {word} hoy")
    public void intentaOtroConsumoHoy(String importe) {
        errorConsumo = catchRuntimeException(() ->
                controles.consumir(lucia, Dinero.de(importe, Moneda.PEN), true, "Tienda en linea"));
    }

    @Entonces("la tarjeta de Lucía pasa inmediatamente a estado CONGELADA")
    public void pasaACongelada() {
        assertThat(estadoControles.estado()).isEqualTo(EstadoDeTarjeta.CONGELADA);
        assertThat(controles.consultarControles(lucia).estado()).isEqualTo(EstadoDeTarjeta.CONGELADA);
    }

    @Y("un intento de consumo con tarjeta de S\\/ {word} es rechazado porque la tarjeta está congelada")
    public void consumoRechazadoPorCongelada(String importe) {
        errorConsumo = catchRuntimeException(() ->
                controles.consumir(lucia, Dinero.de(importe, Moneda.PEN), false, "Farmacia"));
        assertThat(errorConsumo)
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(MotivoDeRechazo.TARJETA_CONGELADA);
    }

    @Y("el saldo disponible de la cuenta de Lucía sigue siendo S\\/ {word}")
    @Y("el saldo disponible de la cuenta de Lucía permanece en S\\/ {word}")
    public void saldoSigueSiendo(String esperado) {
        assertThat(libro.saldoDe(cuentaId, Moneda.PEN)).isEqualTo(Dinero.de(esperado, Moneda.PEN));
    }

    @Entonces("la tarjeta de Lucía vuelve a estado ACTIVA")
    public void vuelveAActiva() {
        assertThat(estadoControles.estado()).isEqualTo(EstadoDeTarjeta.ACTIVA);
        assertThat(controles.consultarControles(lucia).estado()).isEqualTo(EstadoDeTarjeta.ACTIVA);
    }

    @Y("la pista de auditoría y la bandeja de notificaciones registran el congelamiento y descongelamiento")
    public void auditaYNotificaCongelamiento() {
        List<String> auditados = jdbc.queryForList(
                "SELECT tipo FROM core.evento_auditoria WHERE usuario_id = ?", String.class, lucia);
        assertThat(auditados).contains("TARJETA_CONGELADA", "TARJETA_DESCONGELADA", "OPERACION_RECHAZADA");

        List<String> eventosOutbox = jdbc.queryForList(
                "SELECT tipo_evento FROM core.outbox WHERE agregado_tipo = 'Tarjeta'", String.class);
        assertThat(eventosOutbox).contains("TarjetaCongelada", "TarjetaDescongelada");
    }

    @Entonces("el consumo es rechazado por superar el límite diario de la tarjeta")
    public void rechazadoPorLimiteDiario() {
        assertThat(errorConsumo)
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(MotivoDeRechazo.LIMITE_DIARIO_TARJETA_EXCEDIDO);
    }

    @Y("la pista de auditoría registra la actualización del control y el rechazo por límite diario")
    public void auditaControlYRechazoLimite() {
        List<String> auditados = jdbc.queryForList(
                "SELECT tipo FROM core.evento_auditoria WHERE usuario_id = ?", String.class, lucia);
        assertThat(auditados).contains("CONTROL_TARJETA_ACTUALIZADO", "CONSUMO_TARJETA_REALIZADO", "OPERACION_RECHAZADA");

        String motivo = jdbc.queryForObject(
                "SELECT motivo FROM core.evento_auditoria WHERE usuario_id = ? AND tipo = 'OPERACION_RECHAZADA'",
                String.class, lucia);
        assertThat(motivo).isEqualTo("LIMITE_DIARIO_TARJETA_EXCEDIDO");
    }
}
