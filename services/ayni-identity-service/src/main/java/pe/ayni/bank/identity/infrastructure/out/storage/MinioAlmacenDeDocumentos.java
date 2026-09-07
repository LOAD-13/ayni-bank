package pe.ayni.bank.identity.infrastructure.out.storage;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
 * <p>La firma es local (HMAC-SHA256): {@code getPresignedObjectUrl} no hace
 * ninguna llamada de red a MinIO, asi que el unico fallo realista es una
 * configuracion invalida de credenciales, no una caida del servidor.
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
}
