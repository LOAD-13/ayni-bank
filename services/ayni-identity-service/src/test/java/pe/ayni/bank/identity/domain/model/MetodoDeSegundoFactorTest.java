package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetodoDeSegundoFactorTest {

    @Test
    @DisplayName("MetodoDeSegundoFactor se inscribe correctamente sin estar confirmado")
    void testInscribir() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();

        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, null, ahora
        );

        assertEquals(id, metodo.id());
        assertEquals(usuarioId, metodo.usuarioId());
        assertEquals(TipoDeSegundoFactor.CORREO_ELECTRONICO, metodo.tipo());
        assertNull(metodo.secreto());
        assertEquals(ahora, metodo.creadoEn());
        assertFalse(metodo.estaConfirmado());
        assertNull(metodo.confirmadoEn());
    }

    @Test
    @DisplayName("MetodoDeSegundoFactor se confirma correctamente")
    void testConfirmar() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant creado = Instant.now().minusSeconds(60);
        Instant confirmado = Instant.now();

        MetodoDeSegundoFactor metodo = MetodoDeSegundoFactor.inscribir(
                id, usuarioId, TipoDeSegundoFactor.APP_AUTENTICADORA, "SECRET123", creado
        );

        MetodoDeSegundoFactor metodoConfirmado = metodo.confirmar(confirmado);

        assertTrue(metodoConfirmado.estaConfirmado());
        assertEquals(confirmado, metodoConfirmado.confirmadoEn());
        assertEquals("SECRET123", metodoConfirmado.secreto());

        // Confirmar nuevamente debe mantener la primera fecha
        MetodoDeSegundoFactor reconfirmado = metodoConfirmado.confirmar(Instant.now());
        assertEquals(confirmado, reconfirmado.confirmadoEn());
    }

    @Test
    @DisplayName("MetodoDeSegundoFactor valida nulos en el constructor")
    void testValidacionesNulos() {
        UUID id = UUID.randomUUID();
        UUID uId = UUID.randomUUID();
        Instant ahora = Instant.now();

        assertThatThrownBy(() -> MetodoDeSegundoFactor.inscribir(null, uId, TipoDeSegundoFactor.SMS, null, ahora))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> MetodoDeSegundoFactor.inscribir(id, null, TipoDeSegundoFactor.SMS, null, ahora))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> MetodoDeSegundoFactor.inscribir(id, uId, null, null, ahora))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> MetodoDeSegundoFactor.inscribir(id, uId, TipoDeSegundoFactor.SMS, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("equals, hashCode, toString y reconstituir funcionan")
    void testMetodosGenerales() {
        UUID id = UUID.randomUUID();
        UUID uId = UUID.randomUUID();
        Instant ahora = Instant.now();

        MetodoDeSegundoFactor m1 = MetodoDeSegundoFactor.reconstituir(id, uId, TipoDeSegundoFactor.SMS, "sec", ahora, ahora);
        MetodoDeSegundoFactor m2 = MetodoDeSegundoFactor.reconstituir(id, uId, TipoDeSegundoFactor.SMS, "sec", ahora, ahora);

        assertThat(m1).isEqualTo(m2);
        assertThat(m1).isEqualTo(m1);
        assertThat(m1).isNotEqualTo(null);
        assertThat(m1).isNotEqualTo("otro");
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        assertThat(m1.toString()).contains("MetodoDeSegundoFactor");
    }
}
