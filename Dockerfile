# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Multi-stage build for homepage-homelab.
# Stage 1 builds the Spring Boot fat JAR with Maven, folding the static
# frontend (frontend/) into the app's classpath at src/main/resources/static.
# Stage 2 is a slim JRE runtime.
#
# Build context is the repo root (so both backend/ and frontend/ are available).
# See docs/BUILD.md for building on a K3s node without installing Docker.
# ---------------------------------------------------------------------------

# ------------------------------- build stage -------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first: copy only the POM, resolve, then copy sources.
COPY backend/pom.xml ./pom.xml
RUN mvn -q -B -e -DskipTests dependency:go-offline

# App sources
COPY backend/src ./src
# Fold the static frontend into the JAR (served from classpath:/static/).
COPY frontend/ ./src/main/resources/static/

RUN mvn -q -B -DskipTests clean package \
    && cp target/*.jar /workspace/app.jar

# ------------------------------ runtime stage ------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system app && useradd --system --gid app --home /app app
USER app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
