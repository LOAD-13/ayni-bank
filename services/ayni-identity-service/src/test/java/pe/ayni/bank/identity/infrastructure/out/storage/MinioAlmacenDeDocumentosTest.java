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
import java.util.Base64;
import java.util.HexFormat;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InternalException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;

class MinioAlmacenDeDocumentosTest {

    private static final Instant AHORA = Instant.parse("2026-09-06T10:00:00Z");

    /**
     * getPresignedPostFormData firma en local (HMAC-SHA256): no hace ninguna llamada de red a
     * MinIO, pero SI necesita conocer la region del bucket. Sin fijarla explicitamente en el
     * builder, el SDK intenta resolverla con una llamada de red (GetBucketLocation) — por eso
     * se fija aqui tambien, igual que en ConfiguracionDeMinio, para que el test no dependa de
     * que haya un MinIO real escuchando.
     */
    @Nested
    class GenerarUrlDeSubida {

        private static final long CINCO_MB = 5L * 1024 * 1024;

        private final MinioClient minioClient = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("ayni_minio", "cambiar_en_local")
                .region("us-east-1")
                .build();

        private final MinioAlmacenDeDocumentos almacen = new MinioAlmacenDeDocumentos(
                minioClient, minioClient, "http://localhost:9000/", "ayni-kyc-documentos",
                Clock.fixed(AHORA, ZoneOffset.UTC));

        @Test
        @DisplayName("el formulario apunta al bucket publico y lleva la clave, el tipo y la firma")
        void generaUnFormularioBienFormado() {
            UrlDeSubida resultado = almacen.generarUrlDeSubida("kyc/abc/anverso-x.jpg", "image/jpeg", CINCO_MB);

            assertThat(resultado.url()).isEqualTo("http://localhost:9000/ayni-kyc-documentos");
            assertThat(resultado.claveDeObjeto()).isEqualTo("kyc/abc/anverso-x.jpg");
            assertThat(resultado.campos())
                    .containsEntry("key", "kyc/abc/anverso-x.jpg")
                    .containsEntry("Content-Type", "image/jpeg")
                    .containsKeys("policy", "x-amz-signature", "x-amz-credential", "X-Amz-Date", "x-amz-algorithm");
        }

        @Test
        @DisplayName("la politica exige la clave, el tipo exacto y como maximo 5 MB: MinIO rechaza lo demas")
        void laPoliticaLimitaTipoYTamano() {
            UrlDeSubida resultado = almacen.generarUrlDeSubida("kyc/abc/anverso-x.jpg", "image/png", CINCO_MB);

            String politica = new String(Base64.getDecoder().decode(resultado.campos().get("policy")),
                    StandardCharsets.UTF_8);
            assertThat(politica)
                    .contains("[\"eq\",\"$key\",\"kyc/abc/anverso-x.jpg\"]")
                    .contains("[\"eq\",\"$Content-Type\",\"image/png\"]")
                    .contains("[\"content-length-range\",1,5242880]");
        }

        @Test
        @DisplayName("la vigencia declarada es de 5 minutos, contados desde el reloj inyectado")
        void laVigenciaEsDeCincoMinutos() {
            UrlDeSubida resultado = almacen.generarUrlDeSubida("kyc/abc/reverso-x.jpg", "image/jpeg", CINCO_MB);

            assertThat(resultado.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
            String politica = new String(Base64.getDecoder().decode(resultado.campos().get("policy")),
                    StandardCharsets.UTF_8);
            assertThat(politica).contains("2026-09-06T10:05:00");
        }

        @Test
        @DisplayName("firma con el cliente publico, no con el interno — el navegador no resuelve el host de Docker")
        void firmaConElClientePublicoYNoConElInterno() {
            MinioClient clienteInterno = org.mockito.Mockito.mock(MinioClient.class);
            MinioAlmacenDeDocumentos almacenConClientesDistintos = new MinioAlmacenDeDocumentos(
                    clienteInterno, minioClient, "http://localhost:9000", "ayni-kyc-documentos",
                    Clock.fixed(AHORA, ZoneOffset.UTC));

            UrlDeSubida resultado = almacenConClientesDistintos.generarUrlDeSubida(
                    "kyc/abc/anverso-x.jpg", "image/jpeg", CINCO_MB);

            assertThat(resultado.url()).startsWith("http://localhost:9000/");
            org.mockito.Mockito.verifyNoInteractions(clienteInterno);
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
            almacen = new MinioAlmacenDeDocumentos(minioClient, minioClient, "http://localhost:9000",
                    "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));
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
            almacen = new MinioAlmacenDeDocumentos(minioClient, minioClient, "http://localhost:9000",
                    "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("un fallo de MinIO al firmar se traduce a IllegalStateException")
        void generarUrlDeSubidaTraduceElFallo() throws Exception {
            when(minioClient.getPresignedPostFormData(any()))
                    .thenThrow(new InternalException("fallo simulado"));

            assertThatThrownBy(() -> almacen.generarUrlDeSubida("kyc/abc/anverso-x.jpg", "image/jpeg", 1L))
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

    @Nested
    @ExtendWith(MockitoExtension.class)
    class DescribirYEliminar {

        @Mock
        private MinioClient minioClient;

        private MinioAlmacenDeDocumentos almacen;

        @BeforeEach
        void construirAlmacen() {
            almacen = new MinioAlmacenDeDocumentos(minioClient, minioClient, "http://localhost:9000",
                    "ayni-kyc-documentos", Clock.fixed(AHORA, ZoneOffset.UTC));
        }

        @Test
        @DisplayName("describe el tamano y el tipo del objeto sin descargarlo")
        void describeElObjeto() throws Exception {
            StatObjectResponse estado = org.mockito.Mockito.mock(StatObjectResponse.class);
            when(estado.size()).thenReturn(1234L);
            when(estado.contentType()).thenReturn("image/jpeg");
            when(minioClient.statObject(any())).thenReturn(estado);

            assertThat(almacen.describir("kyc/abc/anverso-x.jpg"))
                    .contains(new ObjetoAlmacenado(1234L, "image/jpeg"));
        }

        @Test
        @DisplayName("una clave que no existe se describe como vacia, no como error")
        void unaClaveInexistenteEsVacia() throws Exception {
            ErrorResponse error = org.mockito.Mockito.mock(ErrorResponse.class);
            when(error.code()).thenReturn("NoSuchKey");
            ErrorResponseException excepcion = org.mockito.Mockito.mock(ErrorResponseException.class);
            when(excepcion.errorResponse()).thenReturn(error);
            when(minioClient.statObject(any())).thenThrow(excepcion);

            assertThat(almacen.describir("kyc/abc/no-existe.jpg")).isEmpty();
        }

        @Test
        @DisplayName("cualquier otro fallo al describir se traduce a IllegalStateException")
        void otroFalloAlDescribirEsUnError() throws Exception {
            when(minioClient.statObject(any())).thenThrow(new InternalException("fallo simulado"));

            assertThatThrownBy(() -> almacen.describir("kyc/abc/anverso-x.jpg"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("eliminar borra el objeto del bucket de KYC")
        void eliminaElObjeto() throws Exception {
            almacen.eliminar("kyc/abc/anverso-x.jpg");

            ArgumentCaptor<RemoveObjectArgs> argumentos = ArgumentCaptor.forClass(RemoveObjectArgs.class);
            org.mockito.Mockito.verify(minioClient).removeObject(argumentos.capture());
            assertThat(argumentos.getValue().bucket()).isEqualTo("ayni-kyc-documentos");
            assertThat(argumentos.getValue().object()).isEqualTo("kyc/abc/anverso-x.jpg");
        }
    }
}
