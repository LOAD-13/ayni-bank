# syntax=docker/dockerfile:1
# Imagen ARM64: la misma corre en la Raspberry Pi 5 (staging) y en Oracle Ampere (produccion).
# Ver ADR-0005.

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY services/ayni-notification-service/pom.xml services/ayni-notification-service/pom.xml
RUN mvn -B -pl services/ayni-notification-service -am dependency:go-offline
COPY services/ayni-notification-service/src services/ayni-notification-service/src
RUN mvn -B -pl services/ayni-notification-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ayni && adduser -S ayni -G ayni
WORKDIR /app
COPY --from=build /build/services/ayni-notification-service/target/*.jar app.jar
RUN chown -R ayni:ayni /app
USER ayni
EXPOSE 8083
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
