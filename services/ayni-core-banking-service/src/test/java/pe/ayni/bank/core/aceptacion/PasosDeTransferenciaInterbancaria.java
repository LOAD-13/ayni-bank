package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import java.math.BigDecimal;
import java.time.Duration;
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
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.infrastructure.interbancaria.ConciliadorInterbancario;
import pe.ayni.bank.core.infrastructure.interbancaria.EstadoInterbancario;
import pe.ayni.bank.core.infrastructure.interbancaria.RechazoInterbancarioException;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService.Resultado;
import pe.ayni.bank.core.infrastructure.interbancaria.TransferenciaInterbancariaService.Solicitud;

/** Pasos de {@code transferencia-interbancaria.feature} · HU-11 · AYNI-263. */
public class PasosDeTransferenciaInterbancaria {

    private static final String CCI_DE_OTRO_BANCO = "00219300001234567886";

    @Autowired
    private AbrirCuentaDeAhorroUseCase abrir;
    @Autowired
    private OperarCuentaUseCase operar;
    @Autowired
    private TransferenciaInterbancariaService interbancarias;
    @Autowired
    private ConciliadorInterbancario conciliador;
    @Autowired
    private CamaraDePrueba camara;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID ana;
    private UUID cuentaDeAna;
    private Resultado resultado;
    private RuntimeException error;

    private Resultado transferir(BigDecimal importe, String cci, UUID clave, String confirmacion) {
        Resultado iniciada = interbancarias.iniciar(new Solicitud(ana, clave, cci, importe, null), confirmacion);
        interbancarias.despachar(iniciada.id());
        return interbancarias.consultar(iniciada.id(), ana);
    }

    private Resultado transferir(BigDecimal importe, String cci) {
        return transferir(importe, cci, UUID.randomUUID(), ContextoDeAceptacion.CONFIRMACION_VALIDA);
    }

    private BigDecimal saldoDeAna() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
                FROM core.asiento WHERE cuenta_id = ?""", BigDecimal.class, cuentaDeAna);
    }

    // ─── Dado ────────────────────────────────────────────────────────────────

    @Dado("que Ana tiene una cuenta Ayni con {importe} soles")
    public void anaTieneCuenta(BigDecimal saldo) {
        reloj.fijar(Instant.now());
        ana = UUID.randomUUID();
        cuentaDeAna = abrir.abrirPara(ana, UUID.randomUUID(), Moneda.PEN).id();
        operar.depositarSimulado(ana, new Dinero(saldo, Moneda.PEN), UUID.randomUUID());
    }

    @Dado("que Ana tiene una transferencia interbancaria de {importe} soles pendiente")
    public void transferenciaPendiente(BigDecimal importe) {
        camara.noResponder();
        resultado = transferir(importe, CCI_DE_OTRO_BANCO);
        assertThat(resultado.estado()).isEqualTo(EstadoInterbancario.PENDIENTE);
    }

    // ─── Cuando ──────────────────────────────────────────────────────────────

    @Cuando("Ana confirma con su segundo factor una transferencia de {importe} soles al CCI {string}")
    public void confirmaUnaTransferencia(BigDecimal importe, String cci) {
        resultado = transferir(importe, cci);
    }

    @Cuando("la cámara de compensación la rechaza")
    public void laCamaraRechaza() {
        camara.responder(EstadoInterbancario.RECHAZADA);
        conciliador.conciliar();
    }

    @Cuando("vence el plazo de espera sin respuesta de la cámara")
    public void venceElPlazo() {
        reloj.fijar(Instant.now().plus(Duration.ofDays(1)));
        conciliador.conciliar();
    }

    @Cuando("Ana intenta transferir {importe} soles al CCI {string}")
    public void intentaTransferir(BigDecimal importe, String cci) {
        error = catchRuntimeException(() -> transferir(importe, cci));
    }

    @Cuando("Ana intenta transferir {importe} soles al CCI {string} sin confirmar")
    public void intentaSinConfirmar(BigDecimal importe, String cci) {
        error = catchRuntimeException(() -> transferir(importe, cci, UUID.randomUUID(), null));
    }

    @Cuando("Ana envía dos veces la misma transferencia de {importe} soles al CCI {string}")
    public void enviaDosVeces(BigDecimal importe, String cci) {
        UUID clave = UUID.randomUUID();
        Resultado primera = transferir(importe, cci, clave, ContextoDeAceptacion.CONFIRMACION_VALIDA);
        // El reintento ni siquiera necesita confirmacion: devuelve la transferencia ya hecha.
        resultado = transferir(importe, cci, clave, null);
        assertThat(resultado.id()).isEqualTo(primera.id());
    }

    @Cuando("Ana guarda el CCI {string} como {string}")
    public void guardaBeneficiario(String cci, String alias) {
        interbancarias.guardarBeneficiario(ana, alias, cci);
    }

    @Y("vuelve a guardarlo como {string}")
    public void vuelveAGuardarlo(String alias) {
        guardaBeneficiario(CCI_DE_OTRO_BANCO, alias);
    }

    // ─── Entonces ────────────────────────────────────────────────────────────

    @Entonces("la cámara de compensación recibe la instrucción")
    public void laCamaraRecibe() {
        assertThat(camara.recibidas()).containsExactly(resultado.id());
    }

    @Entonces("la transferencia queda en estado {word}")
    public void quedaEnEstado(String estado) {
        assertThat(interbancarias.consultar(resultado.id(), ana).estado()).hasToString(estado);
    }

    @Entonces("la transferencia queda en estado {word} con el motivo {word}")
    public void quedaEnEstadoConMotivo(String estado, String motivo) {
        quedaEnEstado(estado);
        assertThat(jdbc.queryForObject("SELECT motivo FROM core.transferencia_interbancaria WHERE id = ?",
                String.class, resultado.id())).isEqualTo(motivo);
    }

    @Entonces("su saldo es de {importe} soles")
    public void suSaldo(BigDecimal saldo) {
        assertThat(saldoDeAna()).isEqualByComparingTo(saldo);
    }

    @Entonces("los asientos de compensación le devuelven el importe")
    public void asientosDeCompensacion() {
        UUID reversion = jdbc.queryForObject("""
                SELECT movimiento_reversion FROM core.transferencia_interbancaria WHERE id = ?""",
                UUID.class, resultado.id());
        BigDecimal sumaDelMovimiento = jdbc.queryForObject("""
                SELECT SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END)
                FROM core.asiento WHERE movimiento_id = ?""", BigDecimal.class, reversion);
        // Partida doble: el abono a Ana y el cargo a compensacion suman cero.
        assertThat(sumaDelMovimiento).isEqualByComparingTo("0");
    }

    @Entonces("se anuncia el resultado {word} para avisarle por correo")
    public void seAnuncia(String estado) {
        Integer eventos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM core.outbox
                WHERE tipo_evento = 'TransferenciaInterbancariaResuelta' AND agregado_id = ?
                  AND carga->>'estado' = ? AND carga->>'usuarioId' = ?""",
                Integer.class, resultado.id(), estado, ana.toString());
        assertThat(eventos).isEqualTo(1);
    }

    @Entonces("la pista de auditoría registra {word} y {word}")
    public void auditoria(String primero, String segundo) {
        List<String> tipos = jdbc.queryForList("""
                SELECT tipo FROM core.evento_auditoria WHERE usuario_id = ? ORDER BY ocurrido_en""",
                String.class, ana);
        assertThat(tipos).contains(primero, segundo);
    }

    @Entonces("la operación se rechaza con el código {word}")
    public void rechazadaConCodigo(String codigo) {
        assertThat(error).isInstanceOf(RechazoInterbancarioException.class);
        assertThat(((RechazoInterbancarioException) error).motivo()).hasToString(codigo);
    }

    @Entonces("la operación pide confirmar con el segundo factor")
    public void pideConfirmacion() {
        assertThat(error).isInstanceOf(ConfirmacionInvalidaException.class);
    }

    @Entonces("no se registra ninguna transferencia interbancaria")
    public void ningunaTransferencia() {
        assertThat(cuantas()).isZero();
    }

    @Entonces("solo hay una transferencia interbancaria registrada")
    public void soloUna() {
        assertThat(cuantas()).isEqualTo(1);
    }

    @Entonces("sus beneficiarios frecuentes son {string}")
    public void beneficiarios(String alias) {
        assertThat(interbancarias.beneficiarios(ana))
                .extracting(TransferenciaInterbancariaService.Beneficiario::alias)
                .containsExactly(alias);
    }

    private Integer cuantas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM core.transferencia_interbancaria WHERE usuario_id = ?",
                Integer.class, ana);
    }
}
