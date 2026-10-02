package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CelularTest {

    @Test
    void aceptaUnMovilPeruanoValido() {
        assertThat(new Celular("987654321").valor()).isEqualTo("987654321");
    }

    @ParameterizedTest
    @ValueSource(strings = {"987 654 321", "987-654-321", "  987654321  "})
    @DisplayName("normaliza los separadores que la gente escribe")
    void eliminaEspaciosYGuiones(String escrito) {
        assertThat(new Celular(escrito).valor()).isEqualTo("987654321");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "12345678",   // ocho digitos
        "1234567890", // diez digitos
        "812345678",  // no empieza en 9: en Peru no es un movil
        "98765432a",
        ""
    })
    void rechazaNumerosQueNoSonMovilesPeruanos(String invalido) {
        assertThatThrownBy(() -> new Celular(invalido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nueve digitos");
    }

    @Test
    void rechazaNulo() {
        assertThatThrownBy(() -> new Celular(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("el enmascarado deja los tres ultimos digitos para que la persona se reconozca")
    void enmascaraTodoMenosLosTresUltimos() {
        assertThat(new Celular("987654321").enmascarado()).isEqualTo("******321");
    }
}
