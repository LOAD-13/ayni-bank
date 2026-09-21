package pe.ayni.bank.identity.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DesafioPorCodigoTest {

    @Test
    @DisplayName("DesafioPorCodigo se genera con 10 minutos de vigencia y 0 intentos")
    void testGenerar() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        String hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, hash, ahora
        );

        assertEquals(id, desafio.id());
        assertEquals(usuarioId, desafio.usuarioId());
        assertEquals(TipoDeSegundoFactor.CORREO_ELECTRONICO, desafio.tipoFactor());
        assertEquals(hash, desafio.hashCodigo());
        assertEquals(0, desafio.intentosRealizados());
        assertEquals(ahora.plus(DesafioPorCodigo.VIGENCIA), desafio.expiraEn());
        assertFalse(desafio.estaExpirado(ahora));
        assertFalse(desafio.estaVerificado());
        assertFalse(desafio.alcanzoMaximoIntentos());
    }

    @Test
    @DisplayName("DesafioPorCodigo expira transcurridos 10 minutos")
    void testExpiracion() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash", ahora
        );

        Instant dentroDe5Min = ahora.plusSeconds(300);
        assertFalse(desafio.estaExpirado(dentroDe5Min));

        Instant dentroDe11Min = ahora.plusSeconds(660);
        assertTrue(desafio.estaExpirado(dentroDe11Min));
    }

    @Test
    @DisplayName("DesafioPorCodigo registra intentos fallidos y detecta el máximo de 3 intentos")
    void testIntentosFallidos() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash", ahora
        );

        DesafioPorCodigo int1 = desafio.registrarIntentoFallido();
        assertEquals(1, int1.intentosRealizados());
        assertFalse(int1.alcanzoMaximoIntentos());

        DesafioPorCodigo int2 = int1.registrarIntentoFallido();
        assertEquals(2, int2.intentosRealizados());
        assertFalse(int2.alcanzoMaximoIntentos());

        DesafioPorCodigo int3 = int2.registrarIntentoFallido();
        assertEquals(3, int3.intentosRealizados());
        assertTrue(int3.alcanzoMaximoIntentos());
    }

    @Test
    @DisplayName("DesafioPorCodigo se marca como verificado")
    void testMarcarVerificado() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();
        DesafioPorCodigo desafio = DesafioPorCodigo.generar(
                id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash", ahora
        );

        Instant verificacion = ahora.plusSeconds(30);
        DesafioPorCodigo verificado = desafio.marcarVerificado(verificacion);

        assertTrue(verificado.estaVerificado());
        assertEquals(verificacion, verificado.verificadoEn());
    }
}
