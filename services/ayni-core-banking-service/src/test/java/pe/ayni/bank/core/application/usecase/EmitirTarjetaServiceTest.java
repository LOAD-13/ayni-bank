package pe.ayni.bank.core.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.core.domain.model.Pan;
import pe.ayni.bank.core.domain.model.Tarjeta;
import pe.ayni.bank.core.domain.port.out.RepositorioDeTarjetasPort;
import pe.ayni.bank.core.domain.service.GeneradorDePan;

/** HU-09: emision idempotente y PAN unico, con dobles en memoria. */
class EmitirTarjetaServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);

    /** Repositorio en memoria que cuenta las veces que se pregunto por un PAN. */
    private static final class EnMemoria implements RepositorioDeTarjetasPort {
        final List<Tarjeta> guardadas = new ArrayList<>();
        final Set<String> ocupados = new HashSet<>();

        @Override
        public Optional<Tarjeta> deTitular(UUID usuarioId) {
            return guardadas.stream().filter(t -> t.usuarioId().equals(usuarioId)).findFirst();
        }

        @Override
        public boolean existePan(Pan pan) {
            return ocupados.contains(pan.valor()) || guardadas.stream().anyMatch(t -> t.pan().equals(pan));
        }

        @Override
        public void guardar(Tarjeta tarjeta) {
            guardadas.add(tarjeta);
        }
    }

    /** Un generador que devuelve, en orden, los PAN que se le dan. */
    private static GeneradorDePan generadorDe(String... pans) {
        var cola = new ArrayList<>(List.of(pans));
        return new GeneradorDePan(new java.util.Random()) {
            @Override
            public Pan generar() {
                return new Pan(cola.remove(0));
            }
        };
    }

    @Test
    @DisplayName("Emite una tarjeta ACTIVA para la cuenta; un segundo intento devuelve la misma")
    void idempotentePorTitular() {
        var repositorio = new EnMemoria();
        var servicio = new EmitirTarjetaService(repositorio, new GeneradorDePan(new java.util.Random(7)), RELOJ);
        UUID titular = UUID.randomUUID();
        UUID cuenta = UUID.randomUUID();

        Tarjeta primera = servicio.emitirPara(titular, cuenta);
        Tarjeta segunda = servicio.emitirPara(titular, cuenta);

        assertThat(segunda).isEqualTo(primera);
        assertThat(repositorio.guardadas).hasSize(1);
        assertThat(primera.cuentaId()).isEqualTo(cuenta);
        assertThat(primera.emitidaEn()).isEqualTo(RELOJ.instant());
    }

    @Test
    @DisplayName("Si el PAN generado ya existe, genera otro: dos clientes nunca comparten tarjeta")
    void reintentaSiElPanChoca() {
        var repositorio = new EnMemoria();
        repositorio.ocupados.add("4999990000000007");
        var servicio = new EmitirTarjetaService(repositorio,
                generadorDe("4999990000000007", "4539578763621486"), RELOJ);

        Tarjeta tarjeta = servicio.emitirPara(UUID.randomUUID(), UUID.randomUUID());

        assertThat(tarjeta.pan().valor()).isEqualTo("4539578763621486");
    }

    @Test
    @DisplayName("Tras cinco choques seguidos se rinde con un error en lugar de reutilizar un PAN")
    void seRindeTrasCincoChoques() {
        var repositorio = new EnMemoria();
        repositorio.ocupados.add("4999990000000007");
        String[] siempreElMismo = new String[EmitirTarjetaService.INTENTOS];
        java.util.Arrays.fill(siempreElMismo, "4999990000000007");
        var servicio = new EmitirTarjetaService(repositorio, generadorDe(siempreElMismo), RELOJ);

        assertThatThrownBy(() -> servicio.emitirPara(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repositorio.guardadas).isEmpty();
    }
}
