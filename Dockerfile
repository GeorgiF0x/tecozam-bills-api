# ── Stage 1: Build ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Build en un solo paso. NO usar `dependency:go-offline`: escanea metadata.xml
# de central-snapshots y reventaba el build cuando jackson-databind:2.19.5-SNAPSHOT
# caducaba (lo intentaba descargar aunque Spring Boot BOM pina 2.18.3).
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
