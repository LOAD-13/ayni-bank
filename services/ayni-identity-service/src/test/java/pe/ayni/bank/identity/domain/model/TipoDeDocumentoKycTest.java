package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** HU-02: tipo de contenido segun la extension declarada al subir un documento KYC. */
class TipoDeDocumentoKycTest {

    @ParameterizedTest
    @CsvSource({
        "png, image/png",
        "PNG, image/png",
        "webp, image/webp",
        "WEBP, image/webp",
    })
    @DisplayName("reconoce png y webp sin importar mayusculas o minusculas")
    void reconoceLasExtensionesConTipoPropio(String extension, String tipoEsperado) {
        assertThat(TipoDeDocumentoKyc.ANVERSO.tipoDeContenidoEsperado(extension))
                .isEqualTo(tipoEsperado);
    }

    @ParameterizedTest
    @EnumSource(TipoDeDocumentoKyc.class)
    @DisplayName("cualquier extension fuera del catalogo propio cae en image/jpeg")
    void usaJpegPorDefecto(TipoDeDocumentoKyc tipo) {
        assertThat(tipo.tipoDeContenidoEsperado("jpg")).isEqualTo("image/jpeg");
        assertThat(tipo.tipoDeContenidoEsperado("jpeg")).isEqualTo("image/jpeg");
        assertThat(tipo.tipoDeContenidoEsperado("bmp")).isEqualTo("image/jpeg");
    }

    @ParameterizedTest
    @CsvSource({"ANVERSO, anverso-", "REVERSO, reverso-", "SELFIE, selfie-"})
    @DisplayName("el prefijo del objeto es el tipo en minusculas, que es lo que comprueba kyc-service")
    void prefijoDeObjeto(TipoDeDocumentoKyc tipo, String prefijo) {
        assertThat(tipo.prefijoDeObjeto()).isEqualTo(prefijo);
    }
}
