package pe.ayni.bank.core.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchRuntimeException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import io.cucumber.java.es.Y;
import pe.ayni.bank.core.aceptacion.ContextoDeAceptacion.RelojDePrueba;
import pe.ayni.bank.core.domain.model.Conversion;
import pe.ayni.bank.core.domain.model.Cotizacion;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.TipoDeCambioCambioException;
import pe.ayni.bank.core.domain.model.TipoDeCambioOficial;
import pe.ayni.bank.core.domain.port.in.AbrirCuentaDeAhorroUseCase;
import pe.ayni.bank.core.domain.port.in.ConfirmarConversionUseCase;
import pe.ayni.bank.core.domain.port.in.CotizarConversionUseCase;
import pe.ayni.bank.core.domain.port.in.OperarCuentaUseCase;
import pe.ayni.bank.core.domain.port.out.LibroMayorPort;
import pe.ayni.bank.core.domain.port.out.RegistroDeConversionesPort;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTipoDeCambioPort;

/** Pasos de {@code conversion-de-moneda.feature} · HU-12 · AYNI-275. */
public class PasosDeConversionDeMoneda {

    /** Lunes 23 de noviembre de 2026, 14:00 en Lima. */
    private static final Instant AHORA = Instant.parse("2026-11-23T19:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private AbrirCuentaDeAhorroUseCase abrir;
    @Autowired
    private OperarCuentaUseCase operar;
    @Autowired
    private CotizarConversionUseCase cotizar;
    @Autowired
    private ConfirmarConversionUseCase confirmar;
    @Autowired
    private RegistroDeConversionesPort conversiones;
    @Autowired
    private RepositorioDeTipoDeCambioPort guardados;
    @Autowired
    private LibroMayorPort libro;
    @Autowired
    private FuenteDeTipoDeCambioDePrueba fuente;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private Cuenta soles;
    private Cuenta dolares;
    private Dinero importe;
    private Cotizacion cotizacion;
    private Conversion conversion;
    private Conversion registrada;
    private RuntimeException error;

    @Dado("que Ana tiene una cuenta en soles con S\\/ {word} y una cuenta en dólares")
    public void anaConCuentas(String saldo) {
        reloj.fijar(AHORA);
        UUID usuario = UUID.randomUUID();
        soles = abrir.abrirPara(usuario, UUID.randomUUID(), Moneda.PEN);
        dolares = abrir.abrirPara(usuario, UUID.randomUUID(), Moneda.USD);
        operar.depositarSimulado(usuario, Dinero.de(saldo, Moneda.PEN), UUID.randomUUID());
    }

    @Y("que el BCRP publicó hoy el tipo de cambio: compra {word} y venta {word}")
    public void bcrpPublico(String compra, String venta) {
        fuente.publicar(LocalDate.of(2026, 11, 23), new BigDecimal(compra), new BigDecimal(venta));
    }

    @Dado("que el BCRP no responde y el último tipo guardado, del {fecha}, es de compra {word} y venta {word}")
    public void bcrpCaidoConTipoGuardado(LocalDate fecha, String compra, String venta) {
        guardados.guardar(new TipoDeCambioOficial(fecha, new BigDecimal(compra), new BigDecimal(venta), "BCRP"),
                fecha.atTime(13, 0).atZone(LIMA).toInstant());
        fuente.dejarDeResponder();
    }

    @Cuando("Ana cotiza la conversión de S\\/ {word} a dólares")
    public void anaCotiza(String monto) {
        importe = Dinero.de(monto, Moneda.PEN);
        cotizacion = cotizar.cotizar(soles.usuarioId(), Moneda.PEN);
    }

    @Entonces("la cotización muestra el tipo aplicado {word} sobre la referencia oficial {word} con el segmento {word}")
    public void cotizacionMuestra(String aplicado, String referencia, String segmento) {
        assertThat(cotizacion.tipo().solesPorDolar()).isEqualByComparingTo(aplicado);
        assertThat(cotizacion.referencia()).isEqualByComparingTo(referencia);
        assertThat(cotizacion.segmento().codigo()).isEqualTo(segmento);
        assertThat(cotizacion.tipo().enCache()).isFalse();
    }

    @Entonces("la cotización avisa que usa el último tipo conocido del {fecha}")
    public void cotizacionAvisaDeCache(LocalDate fecha) {
        assertThat(cotizacion.tipo().enCache()).isTrue();
        assertThat(cotizacion.tipo().obtenidoEn().atZone(LIMA).toLocalDate()).isEqualTo(fecha);
    }

    @Cuando("Ana confirma la conversión con el tipo que vio")
    public void anaConfirma() {
        conversion = confirmar.confirmar(soles.usuarioId(), importe, cotizacion.tipo().solesPorDolar(),
                cotizacion.tipo().enCache(), UUID.randomUUID());
    }

    @Cuando("Ana intenta confirmar sin haber visto ese aviso")
    public void anaConfirmaSinAviso() {
        error = catchRuntimeException(() -> confirmar.confirmar(soles.usuarioId(), importe,
                cotizacion.tipo().solesPorDolar(), false, UUID.randomUUID()));
    }

    @Cuando("Ana confirma la conversión habiendo visto el aviso")
    public void anaConfirmaConAviso() {
        conversion = confirmar.confirmar(soles.usuarioId(), importe, cotizacion.tipo().solesPorDolar(), true,
                UUID.randomUUID());
    }

    @Entonces("la conversión no se realiza y se le devuelve la cotización nueva")
    public void noSeRealiza() {
        assertThat(error).isInstanceOfSatisfying(TipoDeCambioCambioException.class, e -> {
            assertThat(e.nueva().tipo().enCache()).isTrue();
            assertThat(e.nueva().tipo().solesPorDolar()).isEqualByComparingTo(cotizacion.tipo().solesPorDolar());
        });
    }

    @Entonces("el saldo de Ana queda en S\\/ {word} y en US$ {word}")
    public void saldoQueda(String enSoles, String enDolares) {
        saldosSon(enSoles, enDolares);
    }

    @Y("el saldo de Ana sigue en S\\/ {word} y en US$ {word}")
    public void saldoSigue(String enSoles, String enDolares) {
        saldosSon(enSoles, enDolares);
    }

    private void saldosSon(String enSoles, String enDolares) {
        assertThat(libro.saldoDe(soles.id(), Moneda.PEN)).isEqualTo(Dinero.de(enSoles, Moneda.PEN));
        assertThat(libro.saldoDe(dolares.id(), Moneda.USD)).isEqualTo(Dinero.de(enDolares, Moneda.USD));
    }

    @Y("las posiciones cambiarias del banco recogen S\\/ {word} y US$ {word}")
    public void posiciones(String enSoles, String enDolares) {
        assertThat(libro.saldoDe(libro.cuentaDePosicionCambiaria(Moneda.PEN).id(), Moneda.PEN))
                .isEqualTo(Dinero.de(enSoles, Moneda.PEN));
        assertThat(libro.saldoDe(libro.cuentaDePosicionCambiaria(Moneda.USD).id(), Moneda.USD))
                .isEqualTo(Dinero.de(enDolares, Moneda.USD));
    }

    @Y("ningún asiento de la conversión mezcla monedas")
    public void sinMezclarMonedas() {
        assertThat(conversion.salida().asientos()).allSatisfy(a -> assertThat(a.importe().moneda()).isEqualTo(Moneda.PEN));
        assertThat(conversion.entrada().asientos()).allSatisfy(a -> assertThat(a.importe().moneda()).isEqualTo(Moneda.USD));
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT moneda) FROM core.asiento WHERE movimiento_id = ?",
                Integer.class, conversion.salida().id())).isOne();
    }

    @Y("la conversión queda registrada con el tipo en caché")
    public void registradaConCache() {
        assertThat(jdbc.queryForObject("SELECT tipo_en_cache FROM core.conversion WHERE id = ?", Boolean.class,
                conversion.id())).isTrue();
    }

    @Cuando("un auditor consulta la conversión registrada")
    public void auditorConsulta() {
        registrada = conversiones.buscarPorSalida(conversion.salida().id()).orElseThrow();
    }

    @Entonces("el importe recibido US$ {word} se recalcula con el tipo aplicado y el importe de origen")
    public void seRecalcula(String recibido) {
        assertThat(registrada.tipo().convertir(registrada.importeOrigen())).isEqualTo(Dinero.de(recibido, Moneda.USD));
        assertThat(registrada.importeDestino()).isEqualTo(Dinero.de(recibido, Moneda.USD));
    }

    @Y("el tipo aplicado es la referencia oficial más la mitad del margen del segmento")
    public void tipoDesdeLaReferencia() {
        BigDecimal mitad = registrada.cotizacion().spread().divide(new BigDecimal("2"));
        BigDecimal esperado = registrada.cotizacion().referencia().multiply(BigDecimal.ONE.add(mitad))
                .setScale(6, java.math.RoundingMode.HALF_EVEN);
        assertThat(registrada.tipo().solesPorDolar()).isEqualByComparingTo(esperado);
        assertThat(registrada.cotizacion().segmento()).isEqualTo("ESTANDAR");
        assertThat(registrada.cotizacion().fuente()).isEqualTo("BCRP");
    }

    @Y("la conversión registrada no se puede modificar ni borrar")
    public void esInalterable() {
        assertThatThrownBy(() -> jdbc.update("UPDATE core.conversion SET tipo_aplicado = 1 WHERE id = ?",
                registrada.id())).isInstanceOfSatisfying(DataAccessException.class,
                e -> assertThat(estadoSql(e)).isEqualTo("42501"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM core.conversion WHERE id = ?", registrada.id()))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(estadoSql(e)).isEqualTo("42501"));
    }

    private static String estadoSql(DataAccessException e) {
        return NestedExceptionUtils.getMostSpecificCause(e) instanceof java.sql.SQLException sql
                ? sql.getSQLState() : null;
    }
}
