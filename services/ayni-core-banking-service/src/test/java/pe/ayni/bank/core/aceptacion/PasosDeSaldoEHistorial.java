package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.aceptacion.ContextoDeAceptacion.RelojDePrueba;
import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;

/** Pasos de {@code saldo-e-historial.feature} · HU-08 · T-32. */
public class PasosDeSaldoEHistorial {

    /** Lo mismo que pide el panel por defecto. */
    private static final int ULTIMOS_MOVIMIENTOS = 20;

    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private AbrirCuentaDeAhorroUseCase abrir;
    @Autowired
    private OperarCuentaUseCase operar;
    @Autowired
    private LibroMayorPort libro;

    private final Map<String, Cuenta> cuentas = new HashMap<>();
    private final Map<String, List<UUID>> depositos = new HashMap<>();
    private Dinero saldo;
    private List<Asiento> movimientos;
    private Optional<Comprobante> comprobanteSolicitado;

    private Cuenta cuentaDe(String nombre) {
        Cuenta cuenta = cuentas.get(nombre);
        assertThat(cuenta).as("%s no tiene cuenta en este escenario", nombre).isNotNull();
        return cuenta;
    }

    private UUID primerDepositoDe(String nombre) {
        return depositos.get(nombre).get(0);
    }

    // ─── Dado ────────────────────────────────────────────────────────────────

    @Dado("que {word} tiene su cuenta Ayni en soles")
    public void tieneCuenta(String nombre) {
        reloj.fijar(Instant.parse("2026-09-30T12:00:00Z"));
        cuentas.put(nombre, abrir.abrirPara(UUID.randomUUID(), UUID.randomUUID(), Moneda.PEN));
    }

    @Y("que {word} recibió estos depósitos:")
    public void recibioDepositos(String nombre, DataTable tabla) {
        Cuenta cuenta = cuentaDe(nombre);
        for (Map<String, String> fila : tabla.asMaps()) {
            UUID movimiento = UUID.randomUUID();
            Instant momento = LocalDate.parse(fila.get("fecha")).atTime(15, 0).toInstant(ZoneOffset.UTC);
            libro.registrar(Movimiento.depositoSimulado(movimiento, libro.cuentaDeFondeo(Moneda.PEN), cuenta,
                    Dinero.de(fila.get("importe"), Moneda.PEN), momento));
            depositos.computeIfAbsent(nombre, n -> new ArrayList<>()).add(movimiento);
        }
    }

    // ─── Cuando ──────────────────────────────────────────────────────────────

    @Cuando("{word} consulta su saldo y sus últimos movimientos")
    public void consulta(String nombre) {
        Cuenta cuenta = cuentaDe(nombre);
        saldo = libro.saldoDe(cuenta.id(), Moneda.PEN);
        movimientos = libro.ultimosAsientosDe(cuenta.id(), ULTIMOS_MOVIMIENTOS);
    }

    @Cuando("{word} filtra sus movimientos del {fecha} al {fecha}")
    public void filtra(String nombre, LocalDate desde, LocalDate hasta) {
        throw new PendingException("AYNI-329 · HU-08 escenario 1: LibroMayorPort solo ofrece ultimosAsientosDe; "
                + "no hay consulta por rango de fechas, tipo ni importe.");
    }

    @Cuando("{word} solicita el comprobante del depósito de {word}")
    public void solicitaComprobanteAjeno(String quien, String titular) {
        comprobanteSolicitado = operar.comprobante(cuentaDe(quien).usuarioId(), primerDepositoDe(titular));
    }

    // ─── Entonces ────────────────────────────────────────────────────────────

    @Entonces("el saldo disponible de {word} es S\\/ {word}")
    public void saldoDisponible(String nombre, String importe) {
        assertThat(saldo).isEqualTo(Dinero.de(importe, Moneda.PEN));
    }

    @Y("^(\\S+) ve sus movimientos del más reciente al más antiguo: ([\\d.]+), ([\\d.]+) y ([\\d.]+)$")
    public void ordenDeMovimientos(String nombre, String primero, String segundo, String tercero) {
        assertThat(movimientos).extracting(Asiento::cuentaId).containsOnly(cuentaDe(nombre).id());
        assertThat(movimientos).extracting(Asiento::tipo).containsOnly(TipoDeAsiento.ABONO);
        assertThat(movimientos).extracting(Asiento::importe).containsExactly(
                Dinero.de(primero, Moneda.PEN), Dinero.de(segundo, Moneda.PEN), Dinero.de(tercero, Moneda.PEN));
    }

    @Entonces("{word} ve únicamente el depósito de S\\/ {word} del {fecha}")
    public void soloElDelRango(String nombre, String importe, LocalDate dia) {
        throw new PendingException("AYNI-329 · HU-08 escenario 1: no existe el filtro por rango de fechas.");
    }

    @Y("ese movimiento muestra un saldo posterior de S\\/ {word}")
    public void saldoPosterior(String importe) {
        throw new PendingException("AYNI-329 · HU-08 escenario 1: los movimientos no informan el saldo posterior.");
    }

    @Entonces("el sistema le deniega el acceso a {word} sin revelar el movimiento")
    public void accesoDenegado(String nombre) {
        assertThat(comprobanteSolicitado).isEmpty();
    }

    @Y("{word} sí puede consultar el comprobante de su depósito")
    public void titularSiLoVe(String nombre) {
        Optional<Comprobante> propio = operar.comprobante(cuentaDe(nombre).usuarioId(), primerDepositoDe(nombre));
        assertThat(propio).isPresent();
        assertThat(propio.get().movimientoId()).isEqualTo(primerDepositoDe(nombre));
    }

    @Entonces("el intento de {word} queda registrado como evento de seguridad en la pista de auditoría")
    public void eventoDeSeguridad(String nombre) {
        throw new PendingException("AYNI-328 · HU-08 escenario 2: el acceso ajeno se deniega sin dejar rastro "
                + "en core.evento_auditoria.");
    }
}
