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
import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.ConfirmacionInvalidaException;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;

/** Pasos de {@code transferencia-entre-cuentas.feature} · HU-07 · AYNI-231. */
public class PasosDeTransferenciaEntreCuentas {

    private static final Instant AHORA = Instant.parse("2026-10-28T17:00:00Z");

    @Autowired
    private AbrirCuentaDeAhorroUseCase abrir;
    @Autowired
    private OperarCuentaUseCase operar;
    @Autowired
    private LibroMayorPort libro;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private Cuenta carla;
    private Cuenta diego;
    private Comprobante comprobante;
    private Comprobante segundoComprobante;
    private RuntimeException error;

    @Dado("que Carla tiene una cuenta Ayni activa con S\\/ {word}")
    public void carlaConCuenta(String saldo) {
        reloj.fijar(AHORA);
        UUID usuario = UUID.randomUUID();
        carla = abrir.abrirPara(usuario, UUID.randomUUID(), Moneda.PEN);
        operar.depositarSimulado(usuario, Dinero.de(saldo, Moneda.PEN), UUID.randomUUID());
    }

    @Y("que Diego tiene una cuenta Ayni activa con S\\/ {word}")
    public void diegoConCuenta(String saldo) {
        UUID usuario = UUID.randomUUID();
        diego = abrir.abrirPara(usuario, UUID.randomUUID(), Moneda.PEN);
        operar.depositarSimulado(usuario, Dinero.de(saldo, Moneda.PEN), UUID.randomUUID());
    }

    @Cuando("Carla transfiere S\\/ {word} a la cuenta de Diego con el concepto {string} y confirma con su segundo factor")
    public void transfiereExitosamente(String importe, String concepto) {
        comprobante = operar.transferir(
                carla.usuarioId(),
                diego.numero().valor(),
                Dinero.de(importe, Moneda.PEN),
                concepto,
                UUID.randomUUID(),
                ContextoDeAceptacion.CONFIRMACION_VALIDA);
    }

    @Cuando("Carla intenta transferir S\\/ {word} a la cuenta de Diego confirmando con su segundo factor")
    public void intentaTransferirImporte(String importe) {
        error = catchRuntimeException(() -> operar.transferir(
                carla.usuarioId(),
                diego.numero().valor(),
                Dinero.de(importe, Moneda.PEN),
                "Intento",
                UUID.randomUUID(),
                ContextoDeAceptacion.CONFIRMACION_VALIDA));
    }

    @Cuando("Carla intenta transferir S\\/ {word} a su propia cuenta confirmando con su segundo factor")
    public void intentaTransferirAMismaCuenta(String importe) {
        error = catchRuntimeException(() -> operar.transferir(
                carla.usuarioId(),
                carla.numero().valor(),
                Dinero.de(importe, Moneda.PEN),
                "A mi misma",
                UUID.randomUUID(),
                ContextoDeAceptacion.CONFIRMACION_VALIDA));
    }

    @Cuando("Carla intenta transferir S\\/ {word} a la cuenta inexistente {string} confirmando con su segundo factor")
    public void intentaTransferirACuentaInexistente(String importe, String numeroDestino) {
        error = catchRuntimeException(() -> operar.transferir(
                carla.usuarioId(),
                numeroDestino,
                Dinero.de(importe, Moneda.PEN),
                "Destino inexistente",
                UUID.randomUUID(),
                ContextoDeAceptacion.CONFIRMACION_VALIDA));
    }

    @Cuando("Carla intenta transferir S\\/ {word} a la cuenta de Diego sin confirmación de segundo factor")
    public void intentaTransferirSinSegundoFactor(String importe) {
        error = catchRuntimeException(() -> operar.transferir(
                carla.usuarioId(),
                diego.numero().valor(),
                Dinero.de(importe, Moneda.PEN),
                "Sin 2FA",
                UUID.randomUUID(),
                null));
    }

    @Cuando("Carla envía dos veces la misma transferencia de S\\/ {word} a Diego con la misma clave de idempotencia")
    public void enviaDosVecesConMismaClave(String importe) {
        UUID clave = UUID.randomUUID();
        comprobante = operar.transferir(
                carla.usuarioId(),
                diego.numero().valor(),
                Dinero.de(importe, Moneda.PEN),
                "Reintento",
                clave,
                ContextoDeAceptacion.CONFIRMACION_VALIDA);
        segundoComprobante = operar.transferir(
                carla.usuarioId(),
                diego.numero().valor(),
                Dinero.de(importe, Moneda.PEN),
                "Reintento",
                clave,
                ContextoDeAceptacion.CONFIRMACION_VALIDA);
    }

    @Entonces("el saldo de Carla queda en S\\/ {word} y el de Diego en S\\/ {word}")
    @Y("el saldo de Carla sigue siendo S\\/ {word} y el de Diego S\\/ {word}")
    public void verificarSaldos(String saldoCarla, String saldoDiego) {
        assertThat(libro.saldoDe(carla.id(), Moneda.PEN)).isEqualTo(Dinero.de(saldoCarla, Moneda.PEN));
        assertThat(libro.saldoDe(diego.id(), Moneda.PEN)).isEqualTo(Dinero.de(saldoDiego, Moneda.PEN));
    }

    @Y("el movimiento registra un CARGO y un ABONO cuya suma algebraica es S\\/ {word}")
    public void verificarPartidaDoble(String sumaEsperada) {
        List<Asiento> asientos = libro.asientosDelMovimiento(comprobante.movimientoId());
        assertThat(asientos).hasSize(2);
        assertThat(asientos).extracting(Asiento::tipo)
                .containsExactlyInAnyOrder(TipoDeAsiento.CARGO, TipoDeAsiento.ABONO);
        Dinero suma = asientos.stream()
                .map(Asiento::efecto)
                .reduce(Dinero.cero(Moneda.PEN), Dinero::mas);
        assertThat(suma).isEqualTo(Dinero.de(sumaEsperada, Moneda.PEN));
    }

    @Y("se genera un comprobante válido con la cuenta destino enmascarada")
    public void verificarComprobante() {
        assertThat(comprobante.cuentaDestino()).isEqualTo(diego.numero().enmascarado());
        assertThat(operar.comprobante(carla.usuarioId(), comprobante.movimientoId()))
                .isPresent()
                .get()
                .extracting(Comprobante::cuentaDestino)
                .isEqualTo(diego.numero().enmascarado());
    }

    @Entonces("la transferencia se rechaza con el motivo {word}")
    public void verificarRechazo(String motivoEsperado) {
        assertThat(error)
                .isInstanceOf(OperacionRechazadaException.class)
                .extracting(e -> ((OperacionRechazadaException) e).motivo())
                .isEqualTo(MotivoDeRechazo.valueOf(motivoEsperado));
    }

    @Y("el rechazo queda registrado en la pista de auditoría")
    public void verificarAuditoriaRechazo() {
        List<String> motivos = jdbc.queryForList(
                "SELECT motivo FROM core.evento_auditoria WHERE usuario_id = ? AND tipo = 'OPERACION_RECHAZADA'",
                String.class, carla.usuarioId());
        assertThat(motivos).contains("SALDO_INSUFICIENTE");
    }

    @Entonces("el sistema exige la confirmación con segundo factor antes de mover dinero")
    public void verificarExigenciaSegundoFactor() {
        assertThat(error).isInstanceOf(ConfirmacionInvalidaException.class);
    }

    @Entonces("ambas respuestas devuelven el mismo comprobante y el dinero se mueve una sola vez")
    public void verificarIdempotencia() {
        assertThat(segundoComprobante.movimientoId()).isEqualTo(comprobante.movimientoId());
        assertThat(segundoComprobante.importe()).isEqualTo(comprobante.importe());
    }
}
