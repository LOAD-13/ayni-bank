# syntax=docker/dockerfile:1
# Imagen ARM64: la misma corre en la Raspberry Pi 5 (staging) y en Oracle Ampere (produccion).
# Ver ADR-0005.

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Se copian TODOS los pom de los modulos: el padre los declara y Maven
# aborta si falta alguno, aunque solo vayamos a construir uno.
COPY services/ayni-gateway/pom.xml services/ayni-gateway/pom.xml
COPY services/ayni-identity-service/pom.xml services/ayni-identity-service/pom.xml
COPY services/ayni-core-banking-service/pom.xml services/ayni-core-banking-service/pom.xml
COPY services/ayni-notification-service/pom.xml services/ayni-notification-service/pom.xml
RUN mvn -B -pl services/ayni-gateway -am dependency:go-offline
COPY services/ayni-gateway/src services/ayni-gateway/src
RUN mvn -B -pl services/ayni-gateway -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

# La imagen base arrastra paquetes de Alpine con CVE conocidas y ya corregidas
# aguas arriba. Hoy son libexpat 2.8.3-r0 —CVE-2026-66046 y CVE-2026-76641,
# ambas ALTAS, resueltas en 2.8.4-r0— y antes fue OpenSSL. La lista cambia sola:
# la base de datos de Trivy se actualiza a diario y aparecen avisos nuevos sin
# que este repositorio toque una linea.
#
# Por eso se actualiza el arbol completo en lugar de una lista de paquetes
# fijada a mano. Fijarla obliga a editar cinco Dockerfiles cada vez que sale una
# CVE nueva, y a enterarse cuando la integracion ya esta en rojo. Ver el riesgo
# R-16 de la matriz.
#
# Ninguna de estas vulnerabilidades es de la aplicacion: Trivy informa cero
# hallazgos en app.jar.
RUN apk upgrade --no-cache \
    && addgroup -S ayni && adduser -S ayni -G ayni
WORKDIR /app
COPY --from=build /build/services/ayni-gateway/target/*.jar app.jar
RUN chown -R ayni:ayni /app
USER ayni
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
