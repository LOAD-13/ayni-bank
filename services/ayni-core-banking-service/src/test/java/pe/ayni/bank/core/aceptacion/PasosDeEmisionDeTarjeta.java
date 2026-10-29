package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.aceptacion.ContextoDeAceptacion.RelojDePrueba;
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.EstadoDeTarjeta;
import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.PanRevelado;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.in.ConsultarTarjetaUseCase;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada;
import pe.ayni.bank.core.infrastructure.in.messaging.EscuchadorDeSolicitudAprobada.SolicitudAprobada;

/** Pasos de {@code emision-de-tarjeta.feature} · HU-09 · AYNI-245. */
public class PasosDeEmisionDeTarjeta {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");

    @Autowired
    private EscuchadorDeSolicitudAprobada escuchador;
    @Autowired
    private ConsultarTarjetaUseCase tarjetas;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID rosa;
    private SolicitudAprobada aprobacion;
    private Tarjeta tarjeta;
    private PanRevelado revelado;
    private RuntimeException error;

    private Tarjeta tarjetaDeRosa() {
        return tarjetas.deTitular(rosa).orElseThrow(() -> new AssertionError("Rosa no tiene tarjeta"));
    }

    // ─── Dado ────────────────────────────────────────────────────────────────

    @Dado("que se aprueba el KYC de Rosa")
    public void seApruebaElKyc() {
        reloj.fijar(AHORA);
        rosa = UUID.randomUUID();
        aprobacion = new SolicitudAprobada(UUID.randomUUID(), rosa, AHORA.toString());
    }

    @Dado("que Rosa ya tiene su tarjeta de débito virtual")
    public void yaTieneTarjeta() {
        seApruebaElKyc();
        escuchador.alAprobarseLaSolicitud(aprobacion);
        tarjeta = tarjetaDeRosa();
    }

    // ─── Cuando ──────────────────────────────────────────────────────────────

    @Cuando("el sistema procesa la apertura de su cuenta")
    public void procesaLaApertura() {
        escuchador.alAprobarseLaSolicitud(aprobacion);
        // RabbitMQ entrega al menos una vez: la segunda entrega no emite otra tarjeta.
        escuchador.alAprobarseLaSolicitud(aprobacion);
        tarjeta = tarjetaDeRosa();
    }

    @Cuando("pide ver el número completo y confirma con su segundo factor desde la IP {string}")
    public void revelaConSegundoFactor(String ip) {
        revelado = tarjetas.revelar(rosa, ContextoDeAceptacion.CONFIRMACION_VALIDA, UUID.randomUUID(), ip);
    }

    @Cuando("pide ver el número completo sin confirmar")
    public void revelaSinConfirmar() {
        error = catchRuntimeException(() -> tarjetas.revelar(rosa, null, UUID.randomUUID(), "203.0.113.25"));
    }

    // ─── Entonces ────────────────────────────────────────────────────────────

    @Entonces("Rosa tiene una tarjeta de débito virtual ACTIVA asociada a esa cuenta")
    public void tieneTarjetaActiva() {
        UUID cuenta = jdbc.queryForObject("SELECT id FROM core.cuenta WHERE usuario_id = ?", UUID.class, rosa);
        assertThat(tarjeta.estado()).isEqualTo(EstadoDeTarjeta.ACTIVA);
        assertThat(tarjeta.cuentaId()).isEqualTo(cuenta);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM core.tarjeta WHERE usuario_id = ?", Integer.class, rosa))
                .isEqualTo(1);
    }

    @Y("el PAN generado supera la validación de Luhn")
    public void superaLuhn() {
        assertThat(Pan.superaLuhn(tarjeta.pan().valor())).isTrue();
    }

    @Y("el PAN está guardado cifrado y no aparece en claro en la base")
    public void guardadoCifrado() {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT pan_cifrado, pan_huella FROM core.tarjeta WHERE usuario_id = ?", rosa);
        assertThat(fila.get("pan_cifrado").toString()).startsWith("v1:").doesNotContain(tarjeta.pan().valor());
        assertThat(fila.get("pan_huella").toString()).doesNotContain(tarjeta.pan().valor());
    }

    @Y("la tarjeta muestra solo los últimos 4 dígitos")
    public void soloLosUltimos4() {
        assertThat(tarjeta.pan().enmascarado())
                .isEqualTo("•••• •••• •••• " + tarjeta.pan().ultimos4())
                .doesNotContain(tarjeta.pan().valor().substring(0, 12));
    }

    @Y("la tarjeta vence dentro de 4 años")
    public void venceEnCuatroAnos() {
        assertThat(tarjeta.venceEn()).isEqualTo(YearMonth.from(AHORA.atZone(ZoneId.of("America/Lima"))).plusYears(4));
    }

    @Y("la pista de auditoría registra la emisión de la tarjeta")
    public void auditaLaEmision() {
        assertThat(tiposAuditados()).contains("TARJETA_EMITIDA");
    }

    @Entonces("ve el PAN completo durante 30 segundos")
    public void veElPanCompleto() {
        assertThat(revelado.numero().replace(" ", "")).isEqualTo(tarjeta.pan().valor());
        assertThat(Duration.between(AHORA, revelado.visibleHasta())).isEqualTo(Duration.ofSeconds(30));
    }

    @Y("la pista de auditoría registra la revelación con su fecha, hora e IP {string}")
    public void auditaLaRevelacion(String ip) {
        Map<String, Object> evento = jdbc.queryForMap("""
                SELECT movimiento_id, ip, ocurrido_en FROM core.evento_auditoria
                WHERE usuario_id = ? AND tipo = 'PAN_REVELADO'""", rosa);
        assertThat(evento.get("movimiento_id")).isEqualTo(tarjeta.id());
        assertThat(evento.get("ip")).isEqualTo(ip);
        assertThat(evento.get("ocurrido_en")).isNotNull();
    }

    @Entonces("se le pide confirmar con su segundo factor")
    public void seLePideConfirmar() {
        assertThat(error).isInstanceOf(ConfirmacionInvalidaException.class);
    }

    @Y("la pista de auditoría no registra ninguna revelación")
    public void sinRevelacion() {
        assertThat(tiposAuditados()).doesNotContain("PAN_REVELADO");
    }

    private List<String> tiposAuditados() {
        return jdbc.queryForList("SELECT tipo FROM core.evento_auditoria WHERE usuario_id = ?", String.class, rosa);
    }
}
