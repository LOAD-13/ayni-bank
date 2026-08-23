# syntax=docker/dockerfile:1
# Imagen ARM64: la misma corre en la Raspberry Pi 5 (staging) y en Oracle Ampere (produccion).
# Ver ADR-0005.

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY services/ayni-core-banking-service/pom.xml services/ayni-core-banking-service/pom.xml
RUN mvn -B -pl services/ayni-core-banking-service -am dependency:go-offline
COPY services/ayni-core-banking-service/src services/ayni-core-banking-service/src
RUN mvn -B -pl services/ayni-core-banking-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ayni && adduser -S ayni -G ayni
WORKDIR /app
COPY --from=build /build/services/ayni-core-banking-service/target/*.jar app.jar
RUN chown -R ayni:ayni /app
USER ayni
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
