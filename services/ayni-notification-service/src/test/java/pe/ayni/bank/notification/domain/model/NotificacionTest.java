package pe.ayni.bank.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
    private static final PoliticaDeReintentos POLITICA = new PoliticaDeReintentos(Duration.ofSeconds(30), 3);

    private static Notificacion nueva() {
        return Notificacion.de(new AvisoDeMovimiento(UUID.randomUUID(), UUID.randomUUID(),
                TipoDeAviso.DEPOSITO_RECIBIDO, Map.of("importe", "10.00")), AHORA);
    }

    @Test
    @DisplayName("nace pendiente y con turno inmediato")
    void nace() {
        Notificacion n = nueva();
        assertThat(n.estado()).isEqualTo(EstadoDeNotificacion.PENDIENTE);
        assertThat(n.intentos()).isZero();
        assertThat(n.proximoIntentoEn()).isEqualTo(AHORA);
        assertThat(n.creadaEn()).isEqualTo(AHORA);
        assertThat(n.enviadaEn()).isNull();
        assertThat(n.datos()).containsEntry("importe", "10.00");
    }

    @Test
    @DisplayName("al enviarse queda ENVIADA y el intento lo registra")
    void enviada() {
        Notificacion n = nueva();
        IntentoDeEnvio intento = n.marcarEnviada(AHORA.plusSeconds(5));

        assertThat(n.estado()).isEqualTo(EstadoDeNotificacion.ENVIADA);
        assertThat(n.enviadaEn()).isEqualTo(AHORA.plusSeconds(5));
        assertThat(intento.enviado()).isTrue();
        assertThat(intento.numero()).isEqualTo(1);
        assertThat(intento.notificacionId()).isEqualTo(n.id());
        assertThat(intento.detalle()).isNull();
    }

    @Test
    @DisplayName("cada fallo dobla la espera y al agotar el maximo queda FALLIDA")
    void reintentos() {
        Notificacion n = nueva();

        n.registrarFallo("caido", AHORA, POLITICA);
        assertThat(n.proximoIntentoEn()).isEqualTo(AHORA.plusSeconds(30));
        n.registrarFallo("caido", AHORA, POLITICA);
        assertThat(n.proximoIntentoEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(n.estado()).isEqualTo(EstadoDeNotificacion.PENDIENTE);

        IntentoDeEnvio ultimo = n.registrarFallo("caido", AHORA, POLITICA);
        assertThat(n.estado()).isEqualTo(EstadoDeNotificacion.FALLIDA);
        assertThat(ultimo.numero()).isEqualTo(3);
        assertThat(ultimo.enviado()).isFalse();
    }

    @Test
    @DisplayName("una notificacion que ya no esta pendiente no se vuelve a tocar")
    void soloPendiente() {
        Notificacion n = nueva();
        n.marcarEnviada(AHORA);

        assertThatThrownBy(() -> n.marcarEnviada(AHORA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> n.registrarFallo("x", AHORA, POLITICA)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("la politica rechaza valores sin sentido y no desborda con muchos intentos")
    void politica() {
        assertThatThrownBy(() -> new PoliticaDeReintentos(Duration.ZERO, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(null, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(Duration.ofSeconds(-1), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeReintentos(Duration.ofSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);

        PoliticaDeReintentos p = new PoliticaDeReintentos(Duration.ofSeconds(1), 100);
        assertThat(p.esperaTras(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.esperaTras(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(p.esperaTras(90)).isEqualTo(Duration.ofSeconds(1L << 20));
    }

    @Test
    @DisplayName("el detalle de un fallo no guarda direcciones de correo y se recorta a 500")
    void detalleSinCorreo() {
        IntentoDeEnvio i = new IntentoDeEnvio(UUID.randomUUID(), UUID.randomUUID(), 1, false,
                "MessageRejected: identities failed the check: ana.quispe+1@example.pe", AHORA);
        assertThat(i.detalle()).isEqualTo("MessageRejected: identities failed the check: [correo]");

        assertThat(IntentoDeEnvio.sinDatosPersonales("x".repeat(600))).hasSize(500);
    }

    @Test
    @DisplayName("el titular exige correo y no lo muestra en las trazas")
    void titular() {
        UUID id = UUID.randomUUID();
        Titular t = new Titular(id, "ana@example.pe", "  Ana ");

        assertThat(t.nombrePila()).isEqualTo("Ana");
        assertThat(t).hasToString("Titular[usuarioId=" + id + "]");
        assertThat(new Titular(id, "ana@example.pe", null).nombrePila()).isEmpty();
        assertThatThrownBy(() -> new Titular(id, " ", "Ana")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Titular(id, null, "Ana")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la plantilla rellena los marcadores y no deja ninguno sin valor")
    void plantilla() {
        Plantilla p = new Plantilla("Recibiste {{moneda}} {{importe}}", "Saldo {{moneda}} {{saldo}} ($ incluido)");

        MensajeRedactado m = p.rellenar(Map.of("moneda", "PEN", "importe", "1,200.00", "saldo", "$5"));

        assertThat(m.asunto()).isEqualTo("Recibiste PEN 1,200.00");
        assertThat(m.texto()).isEqualTo("Saldo PEN $5 ($ incluido)");
        Map<String, String> incompleto = Map.of("moneda", "PEN");
        assertThatThrownBy(() -> p.rellenar(incompleto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("importe");
    }
}
