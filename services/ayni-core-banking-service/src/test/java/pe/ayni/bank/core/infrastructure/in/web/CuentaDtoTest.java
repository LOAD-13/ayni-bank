package pe.ayni.bank.core.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;
import pe.ayni.bank.core.domain.port.out.RepositorioDeCuentasPort;

/** Lo que muestra la consulta de la cuenta (`/cuentas/mia`) · HU-05 y HU-08. */
class CuentaDtoTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final short PRODUCTO_AHORRO_PEN = 1;

    private final UUID titular = UUID.randomUUID();
    private final CuentasFalsas cuentas = new CuentasFalsas();

    /** Arma el DTO igual que el controlador: saldo derivado de los asientos y TREA vigente. */
    private CuentaDto consultar() {
        Cuenta cuenta = cuentas.buscarActivaDe(titular, Moneda.PEN).orElseThrow();
        return CuentaDto.desde(cuenta, cuenta.saldo(cuentas.asientosDe(cuenta.id())),
                cuentas.treaVigenteDe(cuenta.productoId()).orElse(null));
    }

    private Cuenta abrirCuenta() {
        Cuenta cuenta = Cuenta.abrir(UUID.randomUUID(), titular, PRODUCTO_AHORRO_PEN,
                NumeroDeCuenta.de(Moneda.PEN, 1_000_000_002L), Moneda.PEN, AHORA);
        cuentas.guardar(cuenta);
        return cuenta;
    }

    @Test
    @DisplayName("una cuenta recién abierta se ve con saldo cero")
    void cuentaReciennAbierta() {
        Cuenta cuenta = abrirCuenta();

        CuentaDto dto = consultar();

        assertThat(dto.cuentaId()).isEqualTo(cuenta.id().toString());
        assertThat(dto.estado()).isEqualTo("ACTIVA");
        assertThat(dto.moneda()).isEqualTo("PEN");
        assertThat(dto.saldo()).isEqualTo("0.00");
        assertThat(dto.comisionDeMantenimiento()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("el saldo sale de los asientos, no de un campo guardado")
    void elSaldoSeDeriva() {
        Cuenta cuenta = abrirCuenta();
        cuentas.asientos.add(asiento(cuenta, TipoDeAsiento.ABONO, "1000.00"));
        cuentas.asientos.add(asiento(cuenta, TipoDeAsiento.CARGO, "249.35"));

        assertThat(consultar().saldo()).isEqualTo("750.65");
    }

    @Test
    @DisplayName("el número y el CCI van agrupados y también en crudo, para poder copiarlos")
    void formateaLosIdentificadores() {
        abrirCuenta();

        CuentaDto dto = consultar();

        assertThat(dto.numero()).hasSize(14).doesNotContain("-");
        assertThat(dto.numeroFormateado()).contains("-");
        assertThat(dto.cci()).hasSize(20).doesNotContain("-");
        assertThat(dto.cciFormateado()).contains("-");
        // Los guiones son para leerlo, no para pegarlo: varias bancas rechazan el CCI si
        // llegan con separadores.
        assertThat(dto.cciFormateado().replace("-", "")).isEqualTo(dto.cci());
    }

    @Test
    @DisplayName("la TREA se convierte de fracción a porcentaje en el borde")
    void laTreaSeMuestraComoPorcentaje() {
        // El catálogo guarda 0.045000 porque es la forma en la que la tasa se usa para
        // calcular. Multiplicarla al guardarla obligaría a dividir en cada devengo.
        abrirCuenta();
        cuentas.trea = new BigDecimal("0.045000");

        assertThat(consultar().trea()).isEqualTo("4.50");
    }

    @Test
    void sinTasaVigenteElCampoVieneVacio() {
        abrirCuenta();
        cuentas.trea = null;

        assertThat(consultar().trea()).isNull();
    }

    private static Asiento asiento(Cuenta cuenta, TipoDeAsiento tipo, String importe) {
        return new Asiento(UUID.randomUUID(), cuenta.id(), UUID.randomUUID(), tipo,
                Dinero.de(importe, Moneda.PEN), "Movimiento de prueba", AHORA);
    }

    // ─── Doble ─────────────────────────────────────────────────────────────

    private static final class CuentasFalsas implements RepositorioDeCuentasPort {
        private final List<Cuenta> guardadas = new ArrayList<>();
        private final List<Asiento> asientos = new ArrayList<>();
        private BigDecimal trea = new BigDecimal("0.045000");

        @Override
        public boolean existeCuentaActiva(UUID usuarioId, Moneda moneda) {
            return buscarActivaDe(usuarioId, moneda).isPresent();
        }

        @Override
        public Optional<Cuenta> buscarActivaDe(UUID usuarioId, Moneda moneda) {
            return guardadas.stream()
                    .filter(c -> c.usuarioId().equals(usuarioId) && c.moneda() == moneda)
                    .findFirst();
        }

        @Override
        public long siguienteCorrelativo() {
            return 1_000_000_002L;
        }

        @Override
        public Cuenta guardar(Cuenta cuenta) {
            guardadas.add(cuenta);
            return cuenta;
        }

        @Override
        public List<Asiento> asientosDe(UUID cuentaId) {
            return asientos.stream().filter(a -> a.cuentaId().equals(cuentaId)).toList();
        }

        @Override
        public Optional<BigDecimal> treaVigenteDe(short productoId) {
            return Optional.ofNullable(trea);
        }
    }
}
