package pe.ayni.bank.gateway.documentacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rutas hacia el contrato OpenAPI de cada servicio, que Swagger UI ({@code /api/docs}) agrupa.
 *
 * <p><strong>Solo existen con {@code AYNI_API_DOCS=true}</strong>, que pone el compose local y
 * nunca el de produccion: publicar el mapa completo de la API de un banco le ahorra trabajo a
 * quien la ataca. Sin la variable el bean no se crea, la ruta no resuelve a nada y los servicios
 * tienen ademas su {@code /v3/api-docs} apagado. Ver ADR-0033.
 *
 * <p>kyc-service no tiene ruta publica (ADR-0028); la de su contrato es la unica excepcion, y por
 * eso vive aqui y no en {@code application.yml}: si viviera alli existiria siempre.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true")
public class RutasDeDocumentacion {

    @Bean
    public RouteLocator rutasDeDocumentacion(RouteLocatorBuilder rutas,
                                             @Value("${AYNI_IDENTITY_URI:http://ayni-identity-service:8081}") String identity,
                                             @Value("${AYNI_CORE_URI:http://ayni-core-banking-service:8082}") String core,
                                             @Value("${AYNI_KYC_URI:http://ayni-kyc-service:8000}") String kyc) {
        return rutas.routes()
                .route("docs-identity", r -> r.path("/api/docs/identity")
                        .filters(f -> f.setPath("/v3/api-docs")).uri(identity))
                .route("docs-core-banking", r -> r.path("/api/docs/core-banking")
                        .filters(f -> f.setPath("/v3/api-docs")).uri(core))
                .route("docs-kyc", r -> r.path("/api/docs/kyc")
                        .filters(f -> f.setPath("/openapi.json")).uri(kyc))
                .build();
    }
}
