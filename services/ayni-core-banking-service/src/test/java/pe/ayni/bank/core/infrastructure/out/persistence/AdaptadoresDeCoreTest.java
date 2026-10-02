package pe.ayni.bank.core.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.core.domain.model.Cuenta;
import pe.ayni.bank.core.domain.model.Dinero;
import pe.ayni.bank.core.domain.model.Moneda;
import pe.ayni.bank.core.domain.model.NumeroDeCuenta;
import pe.ayni.bank.core.domain.model.TipoDeAsiento;

/**
 * Los adaptadores de core-banking, con los repositorios simulados.
 *
 * <p>Se comprueba el mapeo entre fila y dominio, que es donde puede romperse algo sin que
 * nadie se entere: que una cuenta releída de la base sea la misma que se guardó, y que los
 * asientos vuelvan con su importe exacto y su signo.
 */
@ExtendWith(MockitoExtension.class)
class AdaptadoresDeCoreTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final short PRODUCTO_AHORRO_PEN = 1;

    private final UUID titular = UUID.randomUUID();

    @Nested
    @DisplayName("Cuentas")
    class Cuentas {

        @Mock
        private CuentaJpaRepository cuentas;

        @Mock
        private AsientoJpaRepository asientos;

        @Mock
        private TasaJpaRepository tasas;

        private AdaptadorRepositorioDeCuentas adaptador() {
            return new AdaptadorRepositorioDeCuentas(cuentas, asientos, tasas);
        }

        private Cuenta unaCuenta() {
            return Cuenta.abrir(UUID.randomUUID(), titular, PRODUCTO_AHORRO_PEN,
                    NumeroDeCuenta.de(Moneda.PEN, 1_000_000_003L), Moneda.PEN, AHORA);
        }

        @Test
        @DisplayName("lo que se guarda es lo que se relee, sin perder nada por el camino")
        void elMapeoEsDeIdaYVuelta() {
            Cuenta original = unaCuenta();
            var fila = ArgumentCaptor.forClass(CuentaEntity.class);

            adaptador().guardar(original);
            verify(cuentas).save(fila.capture());

            when(cuentas.findByUsuarioIdAndMonedaAndEstado(titular, "PEN", "ACTIVA"))
                    .thenReturn(Optional.of(fila.getValue()));

            Cuenta releida = adaptador().buscarActivaDe(titular, Moneda.PEN).orElseThrow();

            assertThat(releida.id()).isEqualTo(original.id());
            assertThat(releida.numero()).isEqualTo(original.numero());
            assertThat(releida.cci()).isEqualTo(original.cci());
            assertThat(releida.moneda()).isEqualTo(Moneda.PEN);
            assertThat(releida.productoId()).isEqualTo(PRODUCTO_AHORRO_PEN);
            assertThat(releida.abiertaEn()).isEqualTo(AHORA);
        }

        @Test
        @DisplayName("solo cuenta como duplicada la que esta ACTIVA")
        void laDuplicidadMiraElEstado() {
            // Una cuenta cerrada no impide abrir otra: el criterio de HU-05 es una activa
            // por titular y moneda, no una en toda la historia.
            when(cuentas.existsByUsuarioIdAndMonedaAndEstado(titular, "PEN", "ACTIVA"))
                    .thenReturn(true);

            assertThat(adaptador().existeCuentaActiva(titular, Moneda.PEN)).isTrue();
        }

        @Test
        void sinCuentaActivaDevuelveVacio() {
            when(cuentas.findByUsuarioIdAndMonedaAndEstado(titular, "USD", "ACTIVA"))
                    .thenReturn(Optional.empty());

            assertThat(adaptador().buscarActivaDe(titular, Moneda.USD)).isEmpty();
        }

        @Test
        @DisplayName("el correlativo lo entrega la base, no un contador en memoria")
        void elCorrelativoVieneDeLaSecuencia() {
            when(cuentas.siguienteCorrelativo()).thenReturn(1_000_000_004L);

            assertThat(adaptador().siguienteCorrelativo()).isEqualTo(1_000_000_004L);
        }

        @Test
        @DisplayName("los asientos vuelven con su importe exacto y su signo")
        void losAsientosConservanElImporte() {
            UUID cuentaId = UUID.randomUUID();
            when(asientos.findByCuentaIdOrderByRegistradoEnAsc(cuentaId)).thenReturn(List.of(
                    new AsientoEntity(UUID.randomUUID(), cuentaId, UUID.randomUUID(),
                            "ABONO", new BigDecimal("1000.00"), "PEN", "Deposito", AHORA),
                    new AsientoEntity(UUID.randomUUID(), cuentaId, UUID.randomUUID(),
                            "CARGO", new BigDecimal("249.35"), "PEN", "Retiro", AHORA)));

            var leidos = adaptador().asientosDe(cuentaId);

            assertThat(leidos).hasSize(2);
            assertThat(leidos.get(0).tipo()).isEqualTo(TipoDeAsiento.ABONO);
            assertThat(leidos.get(0).importe()).isEqualTo(Dinero.de("1000.00", Moneda.PEN));
            assertThat(leidos.get(1).efecto()).isEqualTo(Dinero.de("-249.35", Moneda.PEN));
        }

        @Test
        void laTasaVigenteSaleDelCatalogo() {
            when(tasas.treaVigente(PRODUCTO_AHORRO_PEN))
                    .thenReturn(new BigDecimal("0.045000"));

            assertThat(adaptador().treaVigenteDe(PRODUCTO_AHORRO_PEN))
                    .contains(new BigDecimal("0.045000"));
        }

        @Test
        @DisplayName("un producto sin tasa vigente no revienta: devuelve vacio")
        void sinTasaVigente() {
            when(tasas.treaVigente(PRODUCTO_AHORRO_PEN)).thenReturn(null);

            assertThat(adaptador().treaVigenteDe(PRODUCTO_AHORRO_PEN)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Idempotencia")
    class Idempotencia {

        @Mock
        private OperacionIdempotenteJpaRepository repositorio;

        private AdaptadorRegistroDeIdempotencia adaptador() {
            return new AdaptadorRegistroDeIdempotencia(
                    repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("una operacion ya hecha devuelve su resultado anterior")
        void recuerdaLoQueYaSeHizo() {
            // Es lo que impide que el mismo evento, entregado dos veces por RabbitMQ, abra
            // dos cuentas para la misma persona.
            UUID clave = UUID.randomUUID();
            UUID cuenta = UUID.randomUUID();
            when(repositorio.findById(clave)).thenReturn(Optional.of(
                    new OperacionIdempotenteEntity(clave, cuenta, AHORA)));

            assertThat(adaptador().resultadoDe(clave)).contains(cuenta);
        }

        @Test
        void unaOperacionNuevaNoTieneResultadoPrevio() {
            UUID clave = UUID.randomUUID();
            when(repositorio.findById(clave)).thenReturn(Optional.empty());

            assertThat(adaptador().resultadoDe(clave)).isEmpty();
        }

        @Test
        void guardaLaClaveConSuResultado() {
            adaptador().recordar(UUID.randomUUID(), UUID.randomUUID());

            verify(repositorio).save(any(OperacionIdempotenteEntity.class));
        }
    }
}
