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

# uploads/ debe existir y ser escribible por appuser ANTES del USER switch:
# WORKDIR /app es propiedad de root por defecto, así que sin esto
# Files.createDirectories("./uploads") falla en silencio (permiso denegado,
# no bloqueante por diseño) y ni las fotos de ticket ni los PDF de factura
# se guardan nunca.
RUN chown appuser:appgroup app.jar && \
    mkdir -p uploads && chown -R appuser:appgroup uploads

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
