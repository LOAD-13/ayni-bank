package pe.ayni.bank.identity.infrastructure.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.InternalException;
import okhttp3.Headers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.UrlDeSubida;

class MinioAlmacenDeDocumentosTest {

    private static final Instant AHORA = Instant.parse("2026-09-06T10:00:00Z");

    /**
     * getPresignedObjectUrl firma en local (HMAC-SHA256): no hace ninguna
     * llamada de red a MinIO para firmar en si, pero SI necesita conocer la
     * region del bucket. Sin fijarla explicitamente en el builder, el SDK
     * intenta resolverla con una llamada de red (GetBucketLocation) — por
     * eso se fija aqui tambien, igual que en ConfiguracionDeMinio, para que
     * el test no dependa de que haya un MinIO real escuchando.
     */
    @Nested
    class GenerarUrlDeSubida {

        private final MinioClient minioClient = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("ayni_minio", "cambiar_en_local")
                .region("us-east-1")
                .build();

        private final MinioAlmacenDeDocumentos almacen = new MinioAlmacenDeDocumentos(
                minioClient, minioClient, "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));

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

        @Test
        @DisplayName("firma con el cliente publico, no con el interno — el navegador no resuelve el host de Docker")
        void firmaConElClientePublicoYNoConElInterno() {
            MinioClient clienteInterno = MinioClient.builder()
                    .endpoint("http://minio-interno-no-existe:9000")
                    .credentials("ayni_minio", "cambiar_en_local")
                    .region("us-east-1")
                    .build();
            MinioAlmacenDeDocumentos almacenConClientesDistintos = new MinioAlmacenDeDocumentos(
                    clienteInterno, minioClient, "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));

            UrlDeSubida resultado =
                    almacenConClientesDistintos.generarUrlDeSubida("kyc/abc/anverso-x.jpg", "image/jpeg");

            assertThat(resultado.url())
                    .contains("localhost:9000")
                    .doesNotContain("minio-interno-no-existe");
        }
    }

    /**
     * getObject SI hace una llamada de red real a MinIO: se mockea el
     * cliente en vez de requerir un MinIO real corriendo (AYNI-13 subtarea 8).
     */
    @Nested
    @ExtendWith(MockitoExtension.class)
    class CalcularHash {

        @Mock
        private MinioClient minioClient;

        private MinioAlmacenDeDocumentos almacen;

        // No se inicializa inline junto a la declaracion del campo: Mockito
        // inyecta los @Mock DESPUES de que el constructor del test ya
        // corrio, asi que un `new MinioAlmacenDeDocumentos(minioClient, ...)`
        // inline recibiria minioClient=null. @BeforeEach corre despues de
        // esa inyeccion.
        @BeforeEach
        void construirAlmacen() {
            almacen = new MinioAlmacenDeDocumentos(
                    minioClient, minioClient, "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("calcula el SHA-256 en hexadecimal del contenido descargado")
        void calculaElHashDelContenido() throws Exception {
            byte[] contenido = "contenido-del-documento".getBytes(StandardCharsets.UTF_8);
            when(minioClient.getObject(any())).thenReturn(new GetObjectResponse(
                    Headers.of(), "ayni-kyc-documentos", "us-east-1",
                    "kyc/abc/anverso-x.jpg", new ByteArrayInputStream(contenido)));

            String hash = almacen.calcularHash("kyc/abc/anverso-x.jpg");

            String esperado = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contenido));
            assertThat(hash).isEqualTo(esperado);
        }

        @Test
        @DisplayName("el mismo contenido produce siempre el mismo hash")
        void esDeterministico() throws Exception {
            byte[] contenido = "mismo-contenido".getBytes(StandardCharsets.UTF_8);
            when(minioClient.getObject(any()))
                    .thenReturn(new GetObjectResponse(
                            Headers.of(), "ayni-kyc-documentos", "us-east-1",
                            "kyc/a.jpg", new ByteArrayInputStream(contenido)))
                    .thenReturn(new GetObjectResponse(
                            Headers.of(), "ayni-kyc-documentos", "us-east-1",
                            "kyc/a.jpg", new ByteArrayInputStream(contenido)));

            String primero = almacen.calcularHash("kyc/a.jpg");
            String segundo = almacen.calcularHash("kyc/a.jpg");

            assertThat(primero).isEqualTo(segundo);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ManejoDeErrores {

        @Mock
        private MinioClient minioClient;

        private MinioAlmacenDeDocumentos almacen;

        @BeforeEach
        void construirAlmacen() {
            almacen = new MinioAlmacenDeDocumentos(
                    minioClient, minioClient, "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("un fallo de MinIO al firmar se traduce a IllegalStateException")
        void generarUrlDeSubidaTraduceElFallo() throws Exception {
            when(minioClient.getPresignedObjectUrl(any()))
                    .thenThrow(new InternalException("fallo simulado"));

            assertThatThrownBy(() -> almacen.generarUrlDeSubida("kyc/abc/anverso-x.jpg", "image/jpeg"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No se pudo generar la URL de subida.");
        }

        @Test
        @DisplayName("un fallo de MinIO al descargar se traduce a IllegalStateException")
        void calcularHashTraduceElFallo() throws Exception {
            when(minioClient.getObject(any())).thenThrow(new InternalException("fallo simulado"));

            assertThatThrownBy(() -> almacen.calcularHash("kyc/abc/anverso-x.jpg"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No se pudo calcular el hash del documento.");
        }
    }
}
