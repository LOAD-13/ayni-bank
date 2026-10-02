package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bloqueo progresivo tras intentos fallidos · HU-04 escenario 3. */
class ControlDeAccesoTest {

    private static final Instant AHORA = Instant.parse("2026-08-30T10:15:30Z");
    private static final UUID USUARIO = UUID.randomUUID();

    private static ControlDeAcceso tras(int fallos) {
        ControlDeAcceso control = ControlDeAcceso.limpio(USUARIO);
        for (int i = 0; i < fallos; i++) {
            control = control.registrarFallo(AHORA);
        }
        return control;
    }

    @Test
    @DisplayName("los cinco primeros fallos se cuentan pero no bloquean")
    void toleraCincoFallos() {
        ControlDeAcceso control = tras(ControlDeAcceso.INTENTOS_TOLERADOS);

        assertThat(control.fallosConsecutivos()).isEqualTo(5);
        assertThat(control.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    @DisplayName("el sexto fallo pausa el ingreso")
    void bloqueaAlSexto() {
        ControlDeAcceso control = tras(6);

        assertThat(control.estaBloqueado(AHORA)).isTrue();
        assertThat(control.esperaRestante(AHORA)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("cada fallo posterior duplica la espera")
    void elRetardoEsProgresivo() {
        assertThat(tras(7).esperaRestante(AHORA)).isEqualTo(Duration.ofMinutes(10));
        assertThat(tras(8).esperaRestante(AHORA)).isEqualTo(Duration.ofMinutes(20));
        assertThat(tras(9).esperaRestante(AHORA)).isEqualTo(Duration.ofMinutes(40));
    }

    @Test
    @DisplayName("la espera tiene techo: si no, fallar aposta dejaria fuera al titular")
    void elRetardoTieneTecho() {
        // Sin tope, veinte fallos darian una espera de meses, y cualquiera que conozca un
        // correo podria dejar a esa persona sin acceso a su dinero indefinidamente.
        assertThat(tras(20).esperaRestante(AHORA)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void elBloqueoSeLevantaSolo() {
        ControlDeAcceso control = tras(6);

        assertThat(control.estaBloqueado(AHORA.plus(Duration.ofMinutes(6)))).isFalse();
        assertThat(control.esperaRestante(AHORA.plus(Duration.ofMinutes(6))))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("un acierto borra el historial: cuentan los fallos consecutivos")
    void elAciertoLimpiaElContador() {
        ControlDeAcceso control = tras(4).registrarAcierto();

        assertThat(control.fallosConsecutivos()).isZero();
        assertThat(control.estaBloqueado(AHORA)).isFalse();
    }

    @Test
    @DisplayName("solo el fallo que provoca el bloqueo avisa al titular")
    void avisaUnaSolaVez() {
        // Avisar en cada intento posterior convertiria la proteccion en el ataque: bastaria
        // seguir fallando para inundar de correos a la victima.
        assertThat(tras(5).acabaDeBloquearse()).isFalse();
        assertThat(tras(6).acabaDeBloquearse()).isTrue();
        assertThat(tras(7).acabaDeBloquearse()).isFalse();
        assertThat(tras(12).acabaDeBloquearse()).isFalse();
    }

    @Test
    void rechazaUnContadorNegativoAlReconstituir() {
        assertThatThrownBy(() -> ControlDeAcceso.reconstituir(USUARIO, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noFiltraNadaEnElToString() {
        assertThat(tras(3).toString()).contains("fallos=3").doesNotContain("bloqueadoHasta");
    }
}
