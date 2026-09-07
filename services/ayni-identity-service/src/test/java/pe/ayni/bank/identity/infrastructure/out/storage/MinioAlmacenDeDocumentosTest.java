package pe.ayni.bank.identity.infrastructure.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import io.minio.MinioClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.ayni.bank.identity.domain.model.UrlDeSubida;

/**
 * getPresignedObjectUrl firma en local (HMAC-SHA256): no hace ninguna llamada
 * de red a MinIO, asi que no hace falta un MinIO real corriendo para probar
 * que el adaptador arma bien la URL.
 */
class MinioAlmacenDeDocumentosTest {

    private static final Instant AHORA = Instant.parse("2026-09-06T10:00:00Z");

    private final MinioClient minioClient = MinioClient.builder()
            .endpoint("http://localhost:9000")
            .credentials("ayni_minio", "cambiar_en_local")
            .build();

    private final MinioAlmacenDeDocumentos almacen = new MinioAlmacenDeDocumentos(
            minioClient, "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("la URL apunta al bucket y al objeto pedidos, con el metodo PUT firmado")
    void generaUnaUrlBienFormada() {
        UrlDeSubida resultado = almacen.generarUrlDeSubida("kyc/abc/anverso-x.jpg", "image/jpeg");

        assertThat(resultado.url())
                .contains("localhost:9000")
                .contains("/ayni-kyc-documentos/kyc/abc/anverso-x.jpg")
                .contains("X-Amz-Signature");
    }

    @Test
    @DisplayName("la vigencia declarada es de 5 minutos, contados desde el reloj inyectado")
    void laVigenciaEsDeCincoMinutos() {
        UrlDeSubida resultado = almacen.generarUrlDeSubida("kyc/abc/reverso-x.jpg", "image/jpeg");

        assertThat(resultado.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(resultado.url()).contains("X-Amz-Expires=300");
    }
}
