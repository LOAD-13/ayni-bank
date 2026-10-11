package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import pe.ayni.bank.core.application.usecase.AbrirCuentaDeAhorroService;
import pe.ayni.bank.core.application.usecase.OperarCuentaService;
import pe.ayni.bank.core.domain.model.Comprobante;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.MotivoDeRechazo;
import pe.ayni.bank.core.domain.model.OperacionRechazadaException;
import pe.ayni.bank.core.infrastructure.config.ConfiguracionDeTiempo;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorLibroMayor;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPistaDeAuditoria;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorPublicadorDeEventos;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRegistroDeIdempotencia;
import pe.ayni.bank.core.infrastructure.out.persistence.AdaptadorRepositorioDeCuentas;

/**
 * HU-07, escenario 5, contra un PostgreSQL de verdad.
 *
 * <p>Las pruebas del caso de uso usan dobles en memoria, que no tienen bloqueos ni
 * transacciones: con ellos, dos transferencias simultaneas no compiten por nada. Aqui cada
 * hilo abre su propia transaccion contra la misma base que usa produccion, con las
 * migraciones de Flyway aplicadas, y lo que se comprueba es lo que exige el DoD del
 * Sprint 4: <strong>en ningun caso el saldo queda negativo ni se duplica un movimiento</strong>.
 *
 * <p>Sin transaccion propia de la prueba ({@code NOT_SUPPORTED}): si la prueba envolviera
 * los hilos en una transaccion, nunca llegarian a confirmarse y no habria nada que disputar.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({OperarCuentaService.class, AbrirCuentaDeAhorroService.class, AdaptadorLibroMayor.class,
        AdaptadorRepositorioDeCuentas.class, AdaptadorRegistroDeIdempotencia.class,
        AdaptadorPublicadorDeEventos.class, AdaptadorPistaDeAuditoria.class,
        ConfiguracionDeTiempo.class, ObjectMapper.class})
class ConcurrenciaDeTransferenciasTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int HILOS = 8;

    @Autowired
    private OperarCuentaService operar;

    @Autowired
    private AbrirCuentaDeAhorroService abrir;

    @Autowired
    private JdbcTemplate jdbc;

    /** Abre una cuenta para un titular nuevo y le deposita el saldo inicial. */
    private Cuenta cuentaCon(String saldo) {
        UUID titular = UUID.randomUUID();
        Cuenta cuenta = abrir.abrirPara(titular, UUID.randomUUID(), Moneda.PEN);
        if (new BigDecimal(saldo).signum() > 0) {
            operar.depositarSimulado(titular, Dinero.de(saldo, Moneda.PEN), UUID.randomUUID());
        }
        return cuenta;
    }

    private BigDecimal saldoDe(Cuenta cuenta) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
                FROM core.asiento WHERE cuenta_id = ?""", BigDecimal.class, cuenta.id());
    }

    private int movimientosEntre(Cuenta origen, Cuenta destino) {
        return jdbc.queryForObject("""
                SELECT COUNT(DISTINCT a.movimiento_id) FROM core.asiento a
                JOIN core.asiento b ON a.movimiento_id = b.movimiento_id
                WHERE a.cuenta_id = ? AND b.cuenta_id = ?""", Integer.class, origen.id(), destino.id());
    }

    /** Lanza todas las tareas a la vez, tras una barrera, y espera a que terminen. */
    private <T> List<Future<T>> enParalelo(List<Callable<T>> tareas) throws InterruptedException {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> resultados = new ArrayList<>();
        for (Callable<T> tarea : tareas) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                return tarea.call();
            }));
        }
        salida.countDown();
        hilos.shutdown();
        assertThat(hilos.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return resultados;
    }

    private static Throwable causaDe(Future<?> futuro) {
        try {
            futuro.get();
            return null;
        } catch (Exception e) {
            return e.getCause();
        }
    }

    @Test
    @DisplayName("escenario 5: dos transferencias de S/ 80 sobre S/ 100 en paralelo: solo pasa una")
    void dosSobreElMismoSaldo() throws Exception {
        Cuenta origen = cuentaCon("100.00");
        Cuenta destino = cuentaCon("0");
        UUID titular = jdbc.queryForObject("SELECT usuario_id FROM core.cuenta WHERE id = ?", UUID.class, origen.id());

        List<Callable<Comprobante>> tareas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tareas.add(() -> operar.transferir(titular, destino.numero().valor(),
                    Dinero.de("80.00", Moneda.PEN), "Prueba", UUID.randomUUID()));
        }
        List<Future<Comprobante>> resultados = enParalelo(tareas);

        long exitos = resultados.stream().filter(f -> causaDe(f) == null).count();
        assertThat(exitos).isEqualTo(1);
        assertThat(resultados).filteredOn(f -> causaDe(f) != null).singleElement()
                .satisfies(f -> assertThat(causaDe(f)).isInstanceOf(OperacionRechazadaException.class)
                        .extracting(e -> ((OperacionRechazadaException) e).motivo())
                        .isEqualTo(MotivoDeRechazo.SALDO_INSUFICIENTE));
        assertThat(saldoDe(origen)).isEqualByComparingTo("20.00");
        assertThat(saldoDe(destino)).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("una rafaga de transferencias nunca deja el saldo negativo y el dinero se conserva")
    void rafaga() throws Exception {
        Cuenta origen = cuentaCon("100.00");
        Cuenta destino = cuentaCon("0");
        UUID titular = jdbc.queryForObject("SELECT usuario_id FROM core.cuenta WHERE id = ?", UUID.class, origen.id());

        List<Callable<Comprobante>> tareas = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            tareas.add(() -> operar.transferir(titular, destino.numero().valor(),
                    Dinero.de("30.00", Moneda.PEN), "Rafaga", UUID.randomUUID()));
        }
        long exitos = enParalelo(tareas).stream().filter(f -> causaDe(f) == null).count();

        assertThat(exitos).isEqualTo(3);
        assertThat(saldoDe(origen)).isEqualByComparingTo("10.00");
        assertThat(saldoDe(destino)).isEqualByComparingTo("90.00");
        assertThat(saldoDe(origen).add(saldoDe(destino))).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("escenario 3 en paralelo: la misma clave a la vez mueve el dinero una sola vez y todos reciben el mismo comprobante")
    void mismaClaveEnParalelo() throws Exception {
        Cuenta origen = cuentaCon("500.00");
        Cuenta destino = cuentaCon("0");
        UUID titular = jdbc.queryForObject("SELECT usuario_id FROM core.cuenta WHERE id = ?", UUID.class, origen.id());
        UUID clave = UUID.randomUUID();

        List<Callable<Comprobante>> tareas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tareas.add(() -> operar.transferir(titular, destino.numero().valor(),
                    Dinero.de("200.00", Moneda.PEN), "Reintento", clave));
        }
        List<Future<Comprobante>> resultados = enParalelo(tareas);

        assertThat(resultados).allSatisfy(f -> assertThat(causaDe(f)).isNull());
        assertThat(resultados.stream().map(f -> {
            try {
                return f.get().movimientoId();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).distinct()).hasSize(1);
        assertThat(movimientosEntre(origen, destino)).isEqualTo(1);
        assertThat(saldoDe(origen)).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("transferencias cruzadas A→B y B→A a la vez no se interbloquean")
    void cruzadas() throws Exception {
        Cuenta a = cuentaCon("100.00");
        Cuenta b = cuentaCon("100.00");
        UUID titularA = jdbc.queryForObject("SELECT usuario_id FROM core.cuenta WHERE id = ?", UUID.class, a.id());
        UUID titularB = jdbc.queryForObject("SELECT usuario_id FROM core.cuenta WHERE id = ?", UUID.class, b.id());

        List<Callable<Comprobante>> tareas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tareas.add(() -> operar.transferir(titularA, b.numero().valor(),
                    Dinero.de("10.00", Moneda.PEN), "A a B", UUID.randomUUID()));
            tareas.add(() -> operar.transferir(titularB, a.numero().valor(),
                    Dinero.de("10.00", Moneda.PEN), "B a A", UUID.randomUUID()));
        }
        List<Future<Comprobante>> resultados = enParalelo(tareas);

        assertThat(resultados).allSatisfy(f -> assertThat(causaDe(f)).isNull());
        assertThat(saldoDe(a)).isEqualByComparingTo("100.00");
        assertThat(saldoDe(b)).isEqualByComparingTo("100.00");
    }
}
