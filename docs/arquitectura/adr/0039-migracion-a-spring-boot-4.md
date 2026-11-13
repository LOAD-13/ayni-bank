# ADR-0039 · Migración a Spring Boot 4.0 y Spring Framework 7

**Estado:** Aceptada.
**Fecha:** 13 de noviembre de 2026
**Historia:** AYNI-162

---

## Contexto

Spring Framework 6.2.19, la última de la rama 6.2 y la que gestiona Spring Boot 3.5.16, arrastra tres
vulnerabilidades críticas (CVE-2026-47884, -47890 y -47892) que solo se corrigen en la 7.0.9. Estaban
excluidas en `.trivyignore.yaml` porque no eran explotables aquí (no se usan `XsltView`, SSE ni
`RouterFunction`), pero la exclusión caduca el 30 de noviembre de 2026. Renovarla otra vez solo aplaza el
problema: la rama 6.2 no va a recibir la corrección.

Antes de migrar se comprobó que todas las dependencias tienen una versión compatible publicada:

| Pieza | Antes | Después |
|---|---|---|
| Spring Boot / Spring Framework | 3.5.16 / 6.2.19 | **4.0.9 / 7.0.10** |
| Spring Cloud (gateway) | 2025.0.3 | 2025.1.4 (Gateway 5.0) |
| resilience4j | `resilience4j-spring-boot3` 2.2 / 2.4 | `resilience4j-spring-boot4` 2.4.0 |
| springdoc-openapi | 2.8.17 | 3.0.3 |
| openapi-generator (cliente KYC) | 7.11.0 | 7.27.0 |
| Testcontainers | 1.x | 2.0 |
| Cucumber · ArchUnit · JaCoCo | 7.22 · 1.3 · 0.8.12 | 7.34 · 1.4 · 0.8.14 |

Se eligió la **4.0** y no la 4.1 porque Spring Cloud 2025.1 está construido para Spring Boot 4.0; la
combinación con la 4.1 no está soportada todavía.

## Decisión

1. El POM padre pasa a Spring Boot 4.0.9. Se quitan los parches de versión de Netty, Tomcat, el driver de
   PostgreSQL y Jackson 2, que la 4.0.9 ya gestiona en versiones corregidas. Se mantiene el de
   `amqp-client` (5.36.0), porque Spring Boot 4.0.9 aún gestiona la 5.27.1.
2. **Jackson 3** en el código propio: los conversores de RabbitMQ usan `JacksonJsonMessageConverter` con el
   `JsonMapper` que configura Spring Boot, y el outbox y las notificaciones serializan con él. Las anotaciones
   (`com.fasterxml.jackson.annotation`) no cambian de paquete.
3. El cliente KYC generado sigue con **Jackson 2**, que es lo que produce openapi-generator. Construye su propio
   mapeador y no depende del de Spring Boot, así que conviven sin chocar.
4. Spring Boot 4 separa las pruebas por capas: cada servicio declara solo el módulo que usa
   (`spring-boot-starter-data-jpa-test`, `-webmvc-test` o `-webflux-test`). Flyway entra por
   `spring-boot-starter-flyway`; sin él, Spring Boot 4 no ejecuta las migraciones.
   El `RestClient.Builder` del adaptador de la cámara (HU-11) llega con `spring-boot-starter-restclient`.
5. Se vacía `.trivyignore.yaml`: las tres excepciones dejan de hacer falta.

## Consecuencias

- La puerta de Trivy vuelve a estar sin excepciones y no hay fecha de caducidad pendiente.
- Jackson 3 escribe las fechas en ISO 8601 y trata `java.time` sin módulos extra, igual que se configuraba
  en la 3.5; el JSON de los eventos no cambia de forma.
- Spring Framework 7 retira métodos que ya estaban obsoletos (por ejemplo `HttpHeaders.containsKey`, ahora
  `containsHeader`); las pruebas afectadas se ajustaron.
- Se comprobó el arranque real de los cuatro servicios en Docker Compose y el recorrido E2E completo
  (ingreso con TOTP, transferencia, interbancaria y tarjeta), además de las 939 pruebas de Maven.
- Al desplegar la siguiente release hay que reconstruir las cuatro imágenes Java; no cambia ninguna variable
  de entorno ni ninguna migración.
