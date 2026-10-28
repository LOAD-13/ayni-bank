
package pe.ayni.bank.core.aceptacion;

import io.cucumber.java.Before;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import pe.ayni.bank.core.interbancaria.CamaraPort;
import pe.ayni.bank.core.interbancaria.ConciliadorInterbancario;
import pe.ayni.bank.core.interbancaria.EstadoInterbancario;
import pe.ayni.bank.core.interbancaria.ResolucionInterbancariaService;
import pe.ayni.bank.core.interbancaria.TransferenciaInterbancariaService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TransferenciaInterbancariaSteps {

    private UUID transferenciaId;
    private UUID cuentaOrigen;

    private static final UUID COMPENSACION =
        UUID.fromString(
            "cce00000-0000-4000-8000-000000000001"
        );

    private static final BigDecimal IMPORTE =
        new BigDecimal("100.00");

    private static final String CCI =
        "00212345678901234567";

    private JdbcTemplate db;
    private CamaraPort camara;
    private TransferenciaInterbancariaService servicio;
    private ResolucionInterbancariaService resolucion;
    private ConciliadorInterbancario conciliador;

    private EstadoInterbancario estado;
    private boolean vencida;
    private boolean consultaSolicitada;

    private BigDecimal saldoCliente;
    private BigDecimal saldoCompensacion;

    private final List<String> tiposReversion =
        new ArrayList<>();

    private final List<UUID> movimientosReversion =
        new ArrayList<>();

    @Before
    public void preparar() {

        transferenciaId = UUID.randomUUID();
        cuentaOrigen = UUID.randomUUID();

        db = mock(JdbcTemplate.class);
        camara = mock(CamaraPort.class);

        estado = EstadoInterbancario.PENDIENTE;
        vencida = false;
        consultaSolicitada = false;

        saldoCliente = new BigDecimal("900.00");
        saldoCompensacion = new BigDecimal("100.00");

        tiposReversion.clear();
        movimientosReversion.clear();

        configurarConsultas();
        configurarActualizaciones();

        resolucion = new ResolucionInterbancariaService(db);

        servicio = new TransferenciaInterbancariaService(
            db,
            camara,
            resolucion,
            Duration.ofMinutes(5)
        );

        conciliador = new ConciliadorInterbancario(
            db,
            camara,
            resolucion
        );
    }

    // CONSULTAS SIMULADAS

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void configurarConsultas() {

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            RowMapper mapper = inv.getArgument(1);
            return responderConsulta(sql, mapper);
        }).when(db).query(
            anyString(),
            any(RowMapper.class)
        );

        doAnswer(inv -> {
            String sql = inv.getArgument(0);
            RowMapper mapper = inv.getArgument(1);
            return responderConsulta(sql, mapper);
        }).when(db).query(
            anyString(),
            any(RowMapper.class),
            any(UUID.class)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<?> responderConsulta(
        String sql,
        RowMapper mapper
    ) throws Exception {

        Map<String, Object> fila = new HashMap<>();

        if (sql.contains("SELECT cci_destino, importe")) {

            if (estado != EstadoInterbancario.PENDIENTE) {
                return List.of();
            }

            fila.put("cci_destino", CCI);
            fila.put("importe", IMPORTE);

        } else if (sql.contains("SELECT origen_id, importe")) {

            if (estado != EstadoInterbancario.PENDIENTE) {
                return List.of();
            }

            fila.put("origen_id", cuentaOrigen.toString());
            fila.put("importe", IMPORTE);

        } else if (
            sql.contains("FROM transferencia_interbancaria")
            && (
                sql.contains("ORDER BY vence_en")
                || sql.contains("vence_en <")
                || sql.contains("vence_en <=")
            )
        ) {

            if (!vencida ||
                estado != EstadoInterbancario.PENDIENTE) {
                return List.of();
            }

            fila.put("id", transferenciaId.toString());

        } else {
            return List.of();
        }

        ResultSet rs = mock(ResultSet.class);

        when(rs.getString(anyString())).thenAnswer(call -> {
            String columna = call.getArgument(0);
            Object valor = fila.get(columna);

            return valor == null ? null : valor.toString();
        });

        when(rs.getBigDecimal(anyString())).thenAnswer(call -> {
            String columna = call.getArgument(0);
            return (BigDecimal) fila.get(columna);
        });

        when(rs.getObject(anyString(), eq(UUID.class)))
            .thenAnswer(call -> {
                String columna = call.getArgument(0);
                Object valor = fila.get(columna);

                return valor == null
                    ? null
                    : UUID.fromString(valor.toString());
            });

        return List.of(mapper.mapRow(rs, 0));
    }

    // ACTUALIZACIONES SIMULADAS

    private void configurarActualizaciones() {

        doAnswer(this::registrarActualizacion)
            .when(db).update(
                anyString(),
                any(UUID.class)
            );

        doAnswer(this::registrarActualizacion)
            .when(db).update(
                anyString(),
                any(UUID.class),
                any(UUID.class)
            );

        doAnswer(this::registrarActualizacion)
            .when(db).update(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );

        doAnswer(this::registrarActualizacion)
            .when(db).update(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            );
    }

    private int registrarActualizacion(
        org.mockito.invocation.InvocationOnMock inv
    ) {

        String sql = inv.getArgument(0);
        Object[] args = inv.getArguments();

        if (sql.contains("INSERT INTO asiento")) {

            UUID cuenta = (UUID) args[2];
            UUID movimiento = (UUID) args[3];
            String tipo = (String) args[4];
            BigDecimal importe = (BigDecimal) args[5];

            tiposReversion.add(tipo);
            movimientosReversion.add(movimiento);

            if (
                cuenta.equals(cuentaOrigen)
                && tipo.equals("ABONO")
            ) {
                saldoCliente = saldoCliente.add(importe);
            }

            if (
                cuenta.equals(COMPENSACION)
                && tipo.equals("CARGO")
            ) {
                saldoCompensacion =
                    saldoCompensacion.subtract(importe);
            }
        }

        if (
            sql.contains("COMPLETADA")
            && sql.contains("UPDATE")
        ) {
            estado = EstadoInterbancario.COMPLETADA;
        }

        if (
            sql.contains("RECHAZADA")
            && sql.contains("UPDATE")
        ) {
            estado = EstadoInterbancario.RECHAZADA;
        }

        return 1;
    }

    // ESCENARIO 1: TRANSFERENCIA EXITOSA

    @Dado("que el cliente tiene saldo suficiente")
    public void clienteTieneSaldoSuficiente() {

        Assertions.assertTrue(
            saldoCliente.compareTo(IMPORTE) >= 0
        );

        Assertions.assertEquals(
            EstadoInterbancario.PENDIENTE,
            estado
        );
    }

    @Dado("proporciona un CCI valido")
    public void proporcionaCciValido() {

        Assertions.assertTrue(
            CCI.matches("[0-9]{20}")
        );
    }

    @Cuando("la camara acepta la transferencia")
    public void camaraAceptaTransferencia() {

        when(camara.enviar(
            eq(transferenciaId),
            eq(CCI),
            eq(IMPORTE.toPlainString())
        )).thenReturn(
            EstadoInterbancario.COMPLETADA
        );

        servicio.despachar(transferenciaId);

        verify(camara).enviar(
            transferenciaId,
            CCI,
            IMPORTE.toPlainString()
        );
    }

    @Entonces("el estado debe ser COMPLETADA")
    public void estadoCompletada() {

        Assertions.assertEquals(
            EstadoInterbancario.COMPLETADA,
            estado
        );

        Assertions.assertTrue(
            tiposReversion.isEmpty()
        );
    }

    // ESCENARIO 2: TRANSFERENCIA RECHAZADA

    @Dado("que existe una transferencia pendiente")
    public void existeTransferenciaPendiente() {

        Assertions.assertEquals(
            EstadoInterbancario.PENDIENTE,
            estado
        );
    }

    @Cuando("la camara rechaza la transferencia")
    public void camaraRechazaTransferencia() {

        when(camara.enviar(
            eq(transferenciaId),
            eq(CCI),
            eq(IMPORTE.toPlainString())
        )).thenReturn(
            EstadoInterbancario.RECHAZADA
        );

        servicio.despachar(transferenciaId);

        verify(camara).enviar(
            transferenciaId,
            CCI,
            IMPORTE.toPlainString()
        );
    }

    @Entonces("el estado debe ser RECHAZADA")
    public void estadoRechazada() {

        Assertions.assertEquals(
            EstadoInterbancario.RECHAZADA,
            estado
        );
    }

    @Entonces("se deben generar los asientos de reversion")
    public void seGeneranAsientosReversion() {

        Assertions.assertEquals(
            2,
            tiposReversion.size()
        );

        Assertions.assertTrue(
            tiposReversion.contains("CARGO")
        );

        Assertions.assertTrue(
            tiposReversion.contains("ABONO")
        );

        Assertions.assertEquals(
            movimientosReversion.get(0),
            movimientosReversion.get(1)
        );
    }

    // ESCENARIO 3: TRANSFERENCIA VENCIDA

    @Dado("que existe una transferencia pendiente vencida")
    public void existeTransferenciaVencida() {

        vencida = true;

        Assertions.assertEquals(
            EstadoInterbancario.PENDIENTE,
            estado
        );
    }

    @Cuando("el sistema consulta la camara")
    public void sistemaConsultaCamara() {

        Assertions.assertTrue(vencida);

        consultaSolicitada = true;
    }

    @Cuando("la camara confirma el rechazo")
    public void camaraConfirmaRechazo() {

        Assertions.assertTrue(
            consultaSolicitada
        );

        when(camara.consultar(transferenciaId))
            .thenReturn(
                EstadoInterbancario.RECHAZADA
            );

        conciliador.conciliar();

        verify(camara).consultar(
            transferenciaId
        );
    }

    @Entonces("se debe revertir el importe")
    public void seRevierteImporte() {

        Assertions.assertEquals(
            0,
            new BigDecimal("1000.00")
                .compareTo(saldoCliente)
        );

        Assertions.assertEquals(
            0,
            BigDecimal.ZERO
                .compareTo(saldoCompensacion)
        );

        Assertions.assertEquals(
            2,
            tiposReversion.size()
        );
    }
}
