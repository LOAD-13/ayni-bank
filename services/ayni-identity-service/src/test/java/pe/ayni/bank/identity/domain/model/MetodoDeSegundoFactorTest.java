package pe.ayni.bank.identity.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
}
