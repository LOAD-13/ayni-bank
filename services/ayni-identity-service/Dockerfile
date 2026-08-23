# syntax=docker/dockerfile:1
# Imagen ARM64: la misma corre en la Raspberry Pi 5 (staging) y en Oracle Ampere (produccion).
# Ver ADR-0005.

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY services/ayni-identity-service/pom.xml services/ayni-identity-service/pom.xml
RUN mvn -B -pl services/ayni-identity-service -am dependency:go-offline
COPY services/ayni-identity-service/src services/ayni-identity-service/src
RUN mvn -B -pl services/ayni-identity-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ayni && adduser -S ayni -G ayni
WORKDIR /app
COPY --from=build /build/services/ayni-identity-service/target/*.jar app.jar
RUN chown -R ayni:ayni /app
USER ayni
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
