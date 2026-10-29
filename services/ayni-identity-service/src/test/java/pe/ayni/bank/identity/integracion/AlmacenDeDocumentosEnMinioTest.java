package pe.ayni.bank.identity.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.infrastructure.out.storage.MinioAlmacenDeDocumentos;

/**
 * Interoperabilidad con el almacenamiento de objetos (S3 en producción, MinIO en local).
 *
 * <p>Las demás pruebas del adaptador usan un cliente simulado. Esta habla el protocolo de
 * verdad: un MinIO real en un contenedor recibe el formulario firmado tal como lo envía el
 * navegador, aplica la política (clave, tipo y tamaño) y el adaptador lee lo que quedó
 * guardado. Si la firma, la región o la política dejaran de ser compatibles con S3, falla aquí.
 */
@Testcontainers
class AlmacenDeDocumentosEnMinioTest {

    private static final String USUARIO = "ayni_minio";
    private static final String CLAVE = "clave-de-pruebas-minio";
    private static final String BUCKET = "ayni-kyc-documentos";
    private static final long CINCO_MB = 5L * 1024 * 1024;

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
            .withEnv("MINIO_ROOT_USER", USUARIO)
            .withEnv("MINIO_ROOT_PASSWORD", CLAVE)
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    private static MinioAlmacenDeDocumentos almacen;

    @BeforeAll
    static void crearBucket() throws Exception {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioClient cliente = MinioClient.builder()
                .endpoint(endpoint).credentials(USUARIO, CLAVE).region("us-east-1").build();
        if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            cliente.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
        // Reloj real: MinIO compara la caducidad de la política con su propia hora.
        almacen = new MinioAlmacenDeDocumentos(cliente, cliente, endpoint + "/", BUCKET, Clock.systemUTC());
    }

    @Test
    @DisplayName("el navegador sube con el formulario firmado y el servicio lee el mismo contenido")
    void subidaFirmadaYLectura() throws Exception {
        String clave = "kyc/" + UUID.randomUUID() + "/anverso.jpg";
        byte[] imagen = "imagen-jpeg-de-prueba".getBytes(StandardCharsets.UTF_8);

        UrlDeSubida url = almacen.generarUrlDeSubida(clave, "image/jpeg", CINCO_MB);

        assertThat(subir(url, "image/jpeg", imagen)).isEqualTo(204);
        var guardado = almacen.describir(clave).orElseThrow();
        assertThat(guardado.tamanoBytes()).isEqualTo(imagen.length);
        assertThat(guardado.tipoDeContenido()).isEqualTo("image/jpeg");
        assertThat(almacen.calcularHash(clave))
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(imagen)));
    }

    @Test
    @DisplayName("MinIO rechaza con 403 un tipo distinto del firmado y no queda nada guardado")
    void politicaRechazaOtroTipo() throws Exception {
        String clave = "kyc/" + UUID.randomUUID() + "/anverso.jpg";
        UrlDeSubida url = almacen.generarUrlDeSubida(clave, "image/jpeg", CINCO_MB);

        assertThat(subir(url, "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8))).isEqualTo(403);
        assertThat(almacen.describir(clave)).isEmpty();
    }

    @Test
    @DisplayName("un documento eliminado deja de existir para el servicio")
    void eliminar() throws Exception {
        String clave = "kyc/" + UUID.randomUUID() + "/reverso.jpg";
        UrlDeSubida url = almacen.generarUrlDeSubida(clave, "image/jpeg", CINCO_MB);
        subir(url, "image/jpeg", new byte[] {1, 2, 3});

        almacen.eliminar(clave);

        assertThat(almacen.describir(clave)).isEmpty();
    }

    /** POST multipart como el del navegador: los campos firmados primero y el archivo al final. */
    private static int subir(UrlDeSubida url, String tipo, byte[] contenido) throws Exception {
        String limite = "----ayni" + UUID.randomUUID();
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        Map<String, String> campos = new java.util.LinkedHashMap<>(url.campos());
        campos.put("Content-Type", tipo);
        for (var campo : campos.entrySet()) {
            cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + campo.getKey()
                    + "\"\r\n\r\n" + campo.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        cuerpo.writeBytes(("--" + limite + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"doc\"\r\n"
                + "Content-Type: " + tipo + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(contenido);
        cuerpo.writeBytes(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url.url()))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
