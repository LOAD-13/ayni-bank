package pe.ayni.bank.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** HU-02: tipo de contenido segun la extension declarada al subir un documento KYC. */
class TipoDeDocumentoKycTest {

    @ParameterizedTest
    @CsvSource({
        "pdf, application/pdf",
        "PDF, application/pdf",
        "png, image/png",
        "PNG, image/png",
        "webp, image/webp",
        "WEBP, image/webp",
    })
    @DisplayName("reconoce pdf, png y webp sin importar mayusculas o minusculas")
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
}
