package pe.ayni.bank.identity.infrastructure.out.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.DesafioPorCodigo;
import pe.ayni.bank.identity.domain.model.TipoDeSegundoFactor;

class DesafioPorCodigoEntityTest {

    @Test
    @DisplayName("Convierte correctamente de dominio a entidad y viceversa")
    void testConversion() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant ahora = Instant.now();

        DesafioPorCodigo dominio = DesafioPorCodigo.generar(id, usuarioId, TipoDeSegundoFactor.CORREO_ELECTRONICO, "hash123", ahora);

        DesafioPorCodigoEntity entity = DesafioPorCodigoEntity.desdeDominio(dominio);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(entity.getTipoFactor()).isEqualTo(TipoDeSegundoFactor.CORREO_ELECTRONICO);
        assertThat(entity.getHashCodigo()).isEqualTo("hash123");
        assertThat(entity.getIntentosRealizados()).isZero();
        assertThat(entity.getCreadoEn()).isEqualTo(ahora);
        assertThat(entity.getExpiraEn()).isEqualTo(ahora.plus(DesafioPorCodigo.VIGENCIA));
        assertThat(entity.getVerificadoEn()).isNull();

        DesafioPorCodigo deVuelta = entity.aDominio();
        assertThat(deVuelta).isEqualTo(dominio);
    }

    @Test
    @DisplayName("Setters y getters modifican las propiedades de la entidad")
    void testSettersYGetters() {
        DesafioPorCodigoEntity entity = new DesafioPorCodigoEntity();
        UUID id = UUID.randomUUID();
        UUID uId = UUID.randomUUID();
        Instant ahora = Instant.now();

        entity.setId(id);
        entity.setUsuarioId(uId);
        entity.setTipoFactor(TipoDeSegundoFactor.SMS);
        entity.setHashCodigo("hash456");
        entity.setIntentosRealizados(2);
        entity.setCreadoEn(ahora);
        entity.setExpiraEn(ahora.plusSeconds(300));
        entity.setVerificadoEn(ahora.plusSeconds(60));

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUsuarioId()).isEqualTo(uId);
        assertThat(entity.getTipoFactor()).isEqualTo(TipoDeSegundoFactor.SMS);
        assertThat(entity.getHashCodigo()).isEqualTo("hash456");
        assertThat(entity.getIntentosRealizados()).isEqualTo(2);
        assertThat(entity.getCreadoEn()).isEqualTo(ahora);
        assertThat(entity.getExpiraEn()).isEqualTo(ahora.plusSeconds(300));
        assertThat(entity.getVerificadoEn()).isEqualTo(ahora.plusSeconds(60));
    }
}
