package pe.ayni.bank.identity.infrastructure.out.storage;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.ayni.bank.identity.domain.model.ObjetoAlmacenado;
import pe.ayni.bank.identity.domain.model.UrlDeSubida;
import pe.ayni.bank.identity.domain.port.out.AlmacenDeDocumentosPort;

/**
 * Firma URLs pre-firmadas contra MinIO (compatible S3). Migrar a AWS S3 real
 * es cambiar {@code ayni.minio.endpoint} — ver diseno-base.md §4.1.
 *
 * <p>El calculo de la firma en si es local (HMAC-SHA256), pero
 * {@code getPresignedPostFormData} SI necesita conocer la region del bucket:
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
    private final MinioClient minioClientPublico;
    private final String endpointPublico;
    private final String bucket;
    private final Clock reloj;

    public MinioAlmacenDeDocumentos(MinioClient minioClient,
                                    @Qualifier("minioClientPublico") MinioClient minioClientPublico,
                                    @Value("${ayni.minio.endpoint-publico}") String endpointPublico,
                                    @Value("${ayni.minio.bucket-kyc}") String bucket,
                                    Clock reloj) {
        this.minioClient = minioClient;
        this.minioClientPublico = minioClientPublico;
        this.endpointPublico = endpointPublico.endsWith("/")
                ? endpointPublico.substring(0, endpointPublico.length() - 1)
                : endpointPublico;
        this.bucket = bucket;
        this.reloj = reloj;
    }

    /**
     * Politica POST firmada con el cliente publico (la URL la resuelve el navegador).
     *
     * <p>La politica exige la clave exacta, el tipo de contenido exacto y un tamano entre 1
     * byte y el maximo: MinIO rechaza con 403 cualquier subida que no los cumpla.
     */
    @Override
    public UrlDeSubida generarUrlDeSubida(String claveDeObjeto, String tipoDeContenido, long tamanoMaximoBytes) {
        Instant expiraEn = reloj.instant().plus(VIGENCIA);
        PostPolicy politica = new PostPolicy(bucket, expiraEn.atZone(ZoneOffset.UTC));
        politica.addEqualsCondition("key", claveDeObjeto);
        politica.addEqualsCondition("Content-Type", tipoDeContenido);
        politica.addContentLengthRangeCondition(1L, tamanoMaximoBytes);

        try {
            Map<String, String> campos = new LinkedHashMap<>();
            // key y Content-Type van en el formulario ademas de en la politica: la politica
            // dice que valores se admiten, el formulario los envia.
            campos.put("key", claveDeObjeto);
            campos.put("Content-Type", tipoDeContenido);
            campos.putAll(minioClientPublico.getPresignedPostFormData(politica));
            return new UrlDeSubida(endpointPublico + "/" + bucket, campos, claveDeObjeto, expiraEn);
        } catch (MinioException e) {
            throw new IllegalStateException("No se pudo generar la URL de subida.", e);
        }
    }

    @Override
    public Optional<ObjetoAlmacenado> describir(String claveDeObjeto) {
        try {
            StatObjectResponse estado = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(claveDeObjeto).build());
            return Optional.of(new ObjetoAlmacenado(estado.size(), estado.contentType()));
        } catch (ErrorResponseException e) {
            if (esObjetoInexistente(e)) {
                return Optional.empty();
            }
            throw new IllegalStateException("No se pudo consultar el documento.", e);
        } catch (MinioException e) {
            throw new IllegalStateException("No se pudo consultar el documento.", e);
        }
    }

    @Override
    public void eliminar(String claveDeObjeto) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(claveDeObjeto).build());
        } catch (MinioException e) {
            throw new IllegalStateException("No se pudo eliminar el documento.", e);
        }
    }

    private static boolean esObjetoInexistente(ErrorResponseException e) {
        String codigo = e.errorResponse() == null ? null : e.errorResponse().code();
        return "NoSuchKey".equals(codigo) || "NoSuchObject".equals(codigo);
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
