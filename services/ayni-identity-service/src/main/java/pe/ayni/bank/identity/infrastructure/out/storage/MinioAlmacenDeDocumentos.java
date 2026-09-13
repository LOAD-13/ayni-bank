package pe.ayni.bank.identity.infrastructure.out.storage;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.errors.MinioException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;

/**
 * Firma URLs pre-firmadas contra MinIO (compatible S3). Migrar a AWS S3 real
 * es cambiar {@code ayni.minio.endpoint} — ver diseno-base.md §4.1.
 *
 * <p>El calculo de la firma en si es local (HMAC-SHA256), pero
 * {@code getPresignedObjectUrl} SI necesita conocer la region del bucket:
 * sin fijarla explicitamente en el {@code MinioClient} (ver
 * {@code ConfiguracionDeMinio}), el SDK la resuelve con una llamada de red
 * (GetBucketLocation) antes de firmar. Con la region fijada, no hay
 * round-trip y el unico fallo realista es una configuracion invalida de
 * credenciales, no una caida del servidor.
 */
@Component
public class MinioAlmacenDeDocumentos implements AlmacenDeDocumentosPort {

    /** Ver diseno-base.md §4.1: "URLs pre-firmadas de 5 minutos". */
    private static final Duration VIGENCIA = Duration.ofMinutes(5);

    private final MinioClient minioClient;
    private final String bucket;
    private final Clock reloj;

    public MinioAlmacenDeDocumentos(MinioClient minioClient,
                                    @Value("${ayni.minio.bucket-kyc}") String bucket,
                                    Clock reloj) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.reloj = reloj;
    }

    @Override
    public UrlDeSubida generarUrlDeSubida(String claveDeObjeto, String tipoDeContenido) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.PUT)
                            .bucket(bucket)
                            .object(claveDeObjeto)
                            .expiry((int) VIGENCIA.toSeconds(), TimeUnit.SECONDS)
                            .build());
            return new UrlDeSubida(url, reloj.instant().plus(VIGENCIA));
        } catch (MinioException e) {
            throw new IllegalStateException("No se pudo generar la URL de subida.", e);
        }
    }

    @Override
    public String calcularHash(String claveDeObjeto) {
        try (InputStream flujo = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(claveDeObjeto).build())) {
            byte[] contenido = flujo.readAllBytes();
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(contenido);
            return HexFormat.of().formatHex(resumen);
        } catch (MinioException | IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el hash del documento.", e);
        }
    }
}
