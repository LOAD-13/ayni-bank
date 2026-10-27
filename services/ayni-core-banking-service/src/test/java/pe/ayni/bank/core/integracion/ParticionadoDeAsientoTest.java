package pe.ayni.bank.core.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.ayni.bank.core.infrastructure.out.persistence.MantenimientoDeTablas;

/**
 * AYNI-155 contra un PostgreSQL de verdad: la migracion V8 convierte {@code core.asiento} en una
 * tabla particionada por mes sin perder un solo asiento, y el mantenimiento nocturno crea las
 * particiones por adelantado y purga lo que ya no hace falta.
 *
 * <p>La base se lleva primero hasta V7 y se le cargan asientos de meses distintos, como los que
 * hay en produccion antes del cambio; despues se aplica V8 y se comprueba lo que quedo.
 */
@Testcontainers
class ParticionadoDeAsientoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Cuenta tecnica de fondeo que siembra V3: sirve de cuenta valida para la clave foranea. */
    private static final UUID FONDEO = UUID.fromString("f0de0000-0000-4000-8000-000000000001");

    private static JdbcTemplate jdbc;
    private static BigDecimal saldoAntes;

    private static Flyway flyway(String hasta) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("core").defaultSchema("core")
                .locations("classpath:db/migration");
        return (hasta == null ? config : config.target(hasta)).load();
    }

    @BeforeAll
    static void migrarConAsientosDeVariosMeses() {
        flyway("7").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        for (String instante : List.of("2026-05-14 10:00:00-05", "2026-05-31 23:30:00-05",
                "2026-08-02 08:15:00-05", "2026-10-20 12:00:00-05")) {
            jdbc.update("""
                    INSERT INTO core.asiento (id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto, registrado_en)
                    VALUES (?, ?, ?, 'ABONO', 100.10, 'PEN', 'Deposito de prueba', ?::timestamptz)""",
                    UUID.randomUUID(), FONDEO, UUID.randomUUID(), instante);
        }
        saldoAntes = saldo();

        flyway(null).migrate();
    }

    private static BigDecimal saldo() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'ABONO' THEN importe ELSE -importe END), 0)
                FROM core.asiento WHERE cuenta_id = ?""", BigDecimal.class, FONDEO);
    }

    private static List<String> particiones() {
        return jdbc.queryForList("""
                SELECT c.relname FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                WHERE i.inhparent = 'core.asiento'::regclass ORDER BY c.relname""", String.class);
    }

    private static String particionDe(String instante) {
        return jdbc.queryForObject(
                "SELECT c.relname FROM core.asiento a JOIN pg_class c ON c.oid = a.tableoid WHERE a.registrado_en = ?::timestamptz",
                String.class, instante);
    }

    private static String nombre(LocalDate mes) {
        return "asiento_%d_%02d".formatted(mes.getYear(), mes.getMonthValue());
    }

    @Test
    @DisplayName("asiento queda particionada por rango de registrado_en, con clave (id, registrado_en)")
    void quedaParticionada() {
        String estrategia = jdbc.queryForObject("""
                SELECT partstrat::text FROM pg_partitioned_table WHERE partrelid = 'core.asiento'::regclass""",
                String.class);
        String clave = jdbc.queryForObject("""
                SELECT string_agg(a.attname, ',' ORDER BY k.ord) FROM pg_constraint c
                CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
                JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
                WHERE c.conrelid = 'core.asiento'::regclass AND c.contype = 'p'""", String.class);

        assertThat(estrategia).isEqualTo("r");
        assertThat(clave).isEqualTo("id,registrado_en");
    }

    @Test
    @DisplayName("No se pierde un asiento: mismas filas y mismo saldo que antes de migrar")
    void noSePierdeNada() {
        Integer filas = jdbc.queryForObject("SELECT COUNT(*) FROM core.asiento WHERE cuenta_id = ?", Integer.class, FONDEO);

        assertThat(filas).isEqualTo(4);
        assertThat(saldo()).isEqualByComparingTo(saldoAntes).isEqualByComparingTo("400.40");
    }

    @Test
    @DisplayName("Cada asiento cae en la particion de su mes en hora de Lima, no en la de UTC")
    void cadaAsientoEnSuMes() {
        assertThat(particionDe("2026-05-14 10:00:00-05")).isEqualTo("asiento_2026_05");
        // 23:30 en Lima ya es 1 de junio en UTC: el mes contable es el de Lima.
        assertThat(particionDe("2026-05-31 23:30:00-05")).isEqualTo("asiento_2026_05");
        assertThat(particionDe("2026-08-02 08:15:00-05")).isEqualTo("asiento_2026_08");
    }

    @Test
    @DisplayName("Hay particion desde el mes mas antiguo hasta tres meses por delante, mas la de reserva")
    void particionesHastaTresMesesPorDelante() {
        LocalDate esteMes = LocalDate.now(LIMA).withDayOfMonth(1);

        assertThat(particiones())
                .contains("asiento_2026_05", "asiento_2026_06", "asiento_2026_07", "asiento_default")
                .contains(nombre(esteMes), nombre(esteMes.plusMonths(3)));
    }

    @Test
    @DisplayName("Un asiento fuera de toda particion mensual cae en la de reserva en lugar de rechazarse")
    void reservaRecogeLoQueNoTieneParticion() {
        String lejano = "2031-01-15 09:00:00-05";
        jdbc.update("""
                INSERT INTO core.asiento (id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto, registrado_en)
                VALUES (?, ?, ?, 'CARGO', 1.00, 'PEN', 'Fecha lejana', ?::timestamptz)""",
                UUID.randomUUID(), FONDEO, UUID.randomUUID(), lejano);
        try {
            assertThat(particionDe(lejano)).isEqualTo("asiento_default");
        } finally {
            jdbc.update("DELETE FROM core.asiento WHERE registrado_en = ?::timestamptz", lejano);
        }
    }

    @Test
    @DisplayName("Las restricciones siguen vigentes: importe positivo y cuenta existente")
    void restriccionesVigentes() {
        Integer checks = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'core.asiento'::regclass
                AND conname IN ('fk_asiento_cuenta', 'ck_asiento_tipo', 'ck_asiento_moneda', 'ck_asiento_importe_positivo')""",
                Integer.class);

        assertThat(checks).isEqualTo(4);
    }

    @Test
    @DisplayName("Existen los indices de la tabla y los dos que pidio el taller de diseno fisico")
    void indices() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'core'", String.class);

        assertThat(indices).contains("ix_asiento_cuenta", "ix_asiento_movimiento",
                "ix_cuenta_usuario", "ix_operacion_idempotente_registrado");
    }

    @Test
    @DisplayName("Crear particiones es idempotente: la segunda vez no crea nada ni falla")
    void crearParticionesEsIdempotente() {
        jdbc.queryForObject("SELECT core.crear_particiones_de_asiento(3)", Integer.class);

        assertThat(jdbc.queryForObject("SELECT core.crear_particiones_de_asiento(3)", Integer.class)).isZero();
    }

    @Test
    @DisplayName("El mantenimiento crea las particiones que falten si se le pide mas antelacion")
    void mantenimientoCreaParticiones() {
        LocalDate dentroDeCinco = LocalDate.now(LIMA).withDayOfMonth(1).plusMonths(5);

        new MantenimientoDeTablas(jdbc, 5, 7, 30, 1000).ejecutar();

        assertThat(particiones()).contains(nombre(dentroDeCinco));
    }

    @Test
    @DisplayName("Purga el outbox publicado hace mas de 7 dias, nunca lo pendiente ni lo reciente")
    void purgaOutbox() {
        UUID viejoPublicado = outbox("now() - interval '8 days'", "now() - interval '8 days'");
        UUID recientePublicado = outbox("now() - interval '2 days'", "now() - interval '2 days'");
        UUID viejoPendiente = outbox("now() - interval '30 days'", null);

        new MantenimientoDeTablas(jdbc, 3, 7, 30, 1).ejecutar();

        List<UUID> quedan = jdbc.queryForList("SELECT id FROM core.outbox", UUID.class);
        assertThat(quedan).doesNotContain(viejoPublicado).contains(recientePublicado, viejoPendiente);
    }

    @Test
    @DisplayName("Purga las claves de idempotencia de mas de 30 dias y conserva las recientes")
    void purgaIdempotencia() {
        UUID vieja = UUID.randomUUID();
        UUID reciente = UUID.randomUUID();
        jdbc.update("INSERT INTO core.operacion_idempotente VALUES (?, ?, now() - interval '31 days')", vieja, UUID.randomUUID());
        jdbc.update("INSERT INTO core.operacion_idempotente VALUES (?, ?, now() - interval '29 days')", reciente, UUID.randomUUID());

        new MantenimientoDeTablas(jdbc, 3, 7, 30, 1000).ejecutar();

        List<UUID> quedan = jdbc.queryForList("SELECT clave FROM core.operacion_idempotente", UUID.class);
        assertThat(quedan).doesNotContain(vieja).contains(reciente);
    }

    private static UUID outbox(String creado, String publicado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO core.outbox (id, agregado_tipo, agregado_id, tipo_evento, carga, creado_en, publicado_en) "
                + "VALUES (?, 'Cuenta', ?, 'CuentaAbierta', '{}'::jsonb, " + creado + ", "
                + (publicado == null ? "NULL" : publicado) + ")", id, UUID.randomUUID());
        return id;
    }
}
