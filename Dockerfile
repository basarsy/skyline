# syntax=docker/dockerfile:1
# ── Shared multi-stage Dockerfile for all Skyline microservices ──
# Usage: docker build --build-arg MODULE=skyline-schedule --build-arg APP_PORT=8080 .

# ── Build stage ──────────────────────────────────────────────────
FROM eclipse-temurin:22-jdk-jammy AS build
WORKDIR /workspace

ARG MODULE

# Copy Maven wrapper and pom files first for dependency caching
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy all pom.xml files (root + every module) so Maven can resolve the reactor
COPY pom.xml ./
COPY skyline-common/pom.xml  skyline-common/pom.xml
COPY skyline-gateway/pom.xml skyline-gateway/pom.xml
COPY skyline-auth/pom.xml    skyline-auth/pom.xml
COPY skyline-fleet/pom.xml   skyline-fleet/pom.xml
COPY skyline-route/pom.xml   skyline-route/pom.xml
COPY skyline-schedule/pom.xml    skyline-schedule/pom.xml
COPY skyline-reservation/pom.xml skyline-reservation/pom.xml
COPY skyline-checkin/pom.xml skyline-checkin/pom.xml
COPY skyline-crew/pom.xml   skyline-crew/pom.xml
# Test-only module, but the reactor needs its pom to resolve the root project.
COPY skyline-e2e/pom.xml    skyline-e2e/pom.xml

# Download dependencies (cached unless a pom.xml changes). ~/.m2 is a BuildKit cache mount, shared by
# every module build and kept across builds, so a pom change does not re-download the world.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline -pl ${MODULE} -am

# Copy source code
COPY skyline-common/src skyline-common/src
COPY ${MODULE}/src ${MODULE}/src

# Build the target module (and skyline-common as a transitive dependency). Coverage and the enforcer rules
# belong to `./mvnw verify` in CI, not to the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl ${MODULE} -am -DskipTests -Djacoco.skip=true -Denforcer.skip=true package

# ── Runtime stage ────────────────────────────────────────────────
FROM eclipse-temurin:22-jre-jammy AS runtime

ARG MODULE
ARG APP_PORT=8080

WORKDIR /app

# UTC is the contract for every LocalDateTime (see README, "Dates and times").
ENV TZ=UTC
# Size the heap from the container memory limit, and let the orchestrator restart a JVM that ran out of it.
# Override per container by setting JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Read by the healthcheck (curl ships with this base image).
ENV APP_PORT=${APP_PORT}

# Run as non-root
RUN groupadd -r skyline && useradd -r -g skyline -s /bin/false skyline
RUN chown skyline:skyline /app
USER skyline

# Copy the fat jar from the build stage
COPY --from=build --chown=skyline:skyline /workspace/${MODULE}/target/*.jar app.jar

EXPOSE ${APP_PORT}

# Probe every 2s while starting, so dependants waiting on service_healthy are released promptly.
HEALTHCHECK --interval=10s --start-interval=2s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${APP_PORT}/actuator/health/readiness" > /dev/null || exit 1

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar"]
