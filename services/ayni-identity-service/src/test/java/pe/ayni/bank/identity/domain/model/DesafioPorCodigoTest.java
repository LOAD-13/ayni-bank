package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("DesafioPorCodigo se marca como verificado y previene modificaciones posteriores")
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

        // Intentar volver a marcar verificado o registrar intento no cambia la instancia
        assertThat(verificado.marcarVerificado(Instant.now())).isEqualTo(verificado);
        assertThat(verificado.registrarIntentoFallido()).isEqualTo(verificado);
    }

    @Test
    @DisplayName("Validaciones de nulos, equals, hashCode y toString")
    void testMetodosGenerales() {
        UUID id = UUID.randomUUID();
        UUID uId = UUID.randomUUID();
        Instant ahora = Instant.now();

        assertThatThrownBy(() -> DesafioPorCodigo.generar(null, uId, TipoDeSegundoFactor.SMS, "hash", ahora))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DesafioPorCodigo.generar(id, null, TipoDeSegundoFactor.SMS, "hash", ahora))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DesafioPorCodigo.generar(id, uId, null, "hash", ahora))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DesafioPorCodigo.generar(id, uId, TipoDeSegundoFactor.SMS, null, ahora))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DesafioPorCodigo.generar(id, uId, TipoDeSegundoFactor.SMS, "hash", null))
                .isInstanceOf(NullPointerException.class);

        DesafioPorCodigo d1 = DesafioPorCodigo.reconstituir(id, uId, TipoDeSegundoFactor.SMS, "hash", 0, ahora, ahora.plusSeconds(600), null);
        DesafioPorCodigo d2 = DesafioPorCodigo.reconstituir(id, uId, TipoDeSegundoFactor.SMS, "hash", 0, ahora, ahora.plusSeconds(600), null);
        DesafioPorCodigo d3 = DesafioPorCodigo.reconstituir(UUID.randomUUID(), uId, TipoDeSegundoFactor.SMS, "hash", 0, ahora, ahora.plusSeconds(600), null);

        assertThat(d1)
                .isEqualTo(d2)
                .isNotEqualTo(d3)
                .isNotNull()
                .hasSameHashCodeAs(d2)
                .hasToString("DesafioPorCodigo[id=" + id + ", usuarioId=" + uId + ", tipo=SMS, intentos=0, verificado=false]");

        assertThat(d1.creadoEn()).isEqualTo(ahora);
    }
}
