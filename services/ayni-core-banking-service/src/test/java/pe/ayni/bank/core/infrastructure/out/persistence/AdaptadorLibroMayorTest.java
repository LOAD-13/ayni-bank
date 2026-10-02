package pe.ayni.bank.core.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import pe.ayni.bank.core.domain.model.Asiento;
import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.Movimiento;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;

/** Mapeo del libro mayor entre filas y dominio. */
@ExtendWith(MockitoExtension.class)
class AdaptadorLibroMayorTest {

    private static final Instant AHORA = Instant.parse("2026-10-02T15:00:00Z");
    private static final UUID FONDEO = UUID.fromString("f0de0000-0000-4000-8000-000000000001");

    @Mock
    private CuentaJpaRepository cuentas;

    @Mock
    private AsientoJpaRepository asientos;

    private AdaptadorLibroMayor adaptador() {
        return new AdaptadorLibroMayor(cuentas, asientos, FONDEO);
    }

    private static CuentaEntity fila(UUID id, String numero) {
        Cuenta cuenta = Cuenta.abrir(id, UUID.randomUUID(), (short) 1, new NumeroDeCuenta(numero),
                Moneda.PEN, AHORA);
        return new CuentaEntity(cuenta.id(), cuenta.usuarioId(), cuenta.productoId(),
                cuenta.numero().valor(), cuenta.cci().valor(), "PEN", "ACTIVA", AHORA);
    }

    @Test
    @DisplayName("la cuenta de fondeo no se encuentra por numero: nadie puede transferirle")
    void elFondeoNoEsUnDestino() {
        when(cuentas.findByNumero("00110000000001")).thenReturn(Optional.of(fila(FONDEO, "00110000000001")));
        when(cuentas.findByNumero("00111000000040")).thenReturn(Optional.of(fila(UUID.randomUUID(), "00111000000040")));

        assertThat(adaptador().buscarPorNumero(new NumeroDeCuenta("00110000000001"))).isEmpty();
        assertThat(adaptador().buscarPorNumero(new NumeroDeCuenta("00111000000040"))).isPresent();
    }

    @Test
    @DisplayName("la cuenta de fondeo se resuelve por su id fijo, y solo en soles")
    void cuentaDeFondeo() {
        when(cuentas.findById(FONDEO)).thenReturn(Optional.of(fila(FONDEO, "00110000000001")));

        assertThat(adaptador().cuentaDeFondeo(Moneda.PEN).id()).isEqualTo(FONDEO);
        assertThatThrownBy(() -> adaptador().cuentaDeFondeo(Moneda.USD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sin la fila de fondeo, falla con un mensaje que apunta a la migracion")
    void fondeoAusente() {
        when(cuentas.findById(FONDEO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adaptador().cuentaDeFondeo(Moneda.PEN))
                .hasMessageContaining("V3");
    }

    @Test
    @DisplayName("el saldo lo suma la base y vuelve con dos decimales")
    void saldo() {
        UUID cuenta = UUID.randomUUID();
        when(asientos.saldoDe(cuenta)).thenReturn(new BigDecimal("749.250"));

        assertThat(adaptador().saldoDe(cuenta, Moneda.PEN)).isEqualTo(Dinero.de("749.25", Moneda.PEN));
    }

    @Test
    @DisplayName("registrar escribe los dos asientos del movimiento con su importe exacto")
    @SuppressWarnings("unchecked")
    void registrar() {
        Cuenta origen = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 1_000_000_041L), Moneda.PEN, AHORA);
        Cuenta destino = Cuenta.abrir(UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                NumeroDeCuenta.de(Moneda.PEN, 1_000_000_042L), Moneda.PEN, AHORA);
        Movimiento movimiento = Movimiento.transferencia(UUID.randomUUID(), origen,
                Dinero.de("100", Moneda.PEN), destino, Dinero.de("12.34", Moneda.PEN), "x", AHORA);
        ArgumentCaptor<List<AsientoEntity>> filas = ArgumentCaptor.forClass(List.class);

        adaptador().registrar(movimiento);

        verify(asientos).saveAll(filas.capture());
        assertThat(filas.getValue()).extracting(AsientoEntity::getTipo).containsExactly("CARGO", "ABONO");
        assertThat(filas.getValue()).extracting(AsientoEntity::getImporte)
                .containsOnly(new BigDecimal("12.34"));
    }

    @Test
    @DisplayName("ultimos movimientos y asientos de un movimiento vuelven al dominio sin perder nada")
    void lecturas() {
        UUID cuenta = UUID.randomUUID();
        UUID movimiento = UUID.randomUUID();
        AsientoEntity fila = new AsientoEntity(UUID.randomUUID(), cuenta, movimiento, "ABONO",
                new BigDecimal("300.00"), "PEN", "Deposito simulado", AHORA);
        when(asientos.findByCuentaIdOrderByRegistradoEnDesc(any(), any(Limit.class))).thenReturn(List.of(fila));
        when(asientos.findByMovimientoId(movimiento)).thenReturn(List.of(fila));

        List<Asiento> ultimos = adaptador().ultimosAsientosDe(cuenta, 10);
        assertThat(ultimos).singleElement().satisfies(a -> {
            assertThat(a.tipo()).isEqualTo(TipoDeAsiento.ABONO);
            assertThat(a.importe()).isEqualTo(Dinero.de("300.00", Moneda.PEN));
        });
        assertThat(adaptador().asientosDelMovimiento(movimiento)).hasSize(1);
        verify(asientos).findByCuentaIdOrderByRegistradoEnDesc(cuenta, Limit.of(10));
    }

    @Test
    @DisplayName("bloquear delega en el SELECT ... FOR UPDATE ordenado")
    void bloquear() {
        List<UUID> ids = List.of(UUID.randomUUID());
        adaptador().bloquear(ids);
        verify(cuentas).bloquearPorId(ids);
    }

    @Test
    @DisplayName("buscar por id reconstruye la cuenta")
    void porId() {
        UUID id = UUID.randomUUID();
        when(cuentas.findById(id)).thenReturn(Optional.of(fila(id, "00111000000043")));
        assertThat(adaptador().buscarPorId(id)).map(Cuenta::id).contains(id);
    }
}
