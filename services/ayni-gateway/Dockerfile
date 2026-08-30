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

# La base trae Alpine 3.24.1 con OpenSSL 3.5.7-r0, que arrastra CVE-2026-14456
# (ALTA, agotamiento de memoria en el servidor QUIC), corregida en 3.5.8-r0 y ya
# disponible en el repositorio de Alpine 3.24.
#
# No es una vulnerabilidad de la aplicacion: Trivy informa cero hallazgos en
# app.jar. Es deriva de la imagen base, que se actualiza por su cuenta y puede
# volver a moverse. Al elevar la etiqueta de la base, volver a escanear y
# comprobar si esta linea ya sobra.
RUN apk upgrade --no-cache libcrypto3 libssl3 openssl     && addgroup -S ayni && adduser -S ayni -G ayni
WORKDIR /app
COPY --from=build /build/services/ayni-gateway/target/*.jar app.jar
RUN chown -R ayni:ayni /app
USER ayni
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
