# ── Shared multi-stage Dockerfile for all Skyline microservices ──
# Usage: docker build --build-arg MODULE=skyline-schedule --build-arg APP_PORT=8080 .

# ── Build stage ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
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

# Download dependencies (cached unless a pom.xml changes)
RUN ./mvnw -B dependency:go-offline -pl ${MODULE} -am -q || true

# Copy source code
COPY skyline-common/src skyline-common/src
COPY ${MODULE}/src ${MODULE}/src

# Build the target module (and skyline-common as a transitive dependency)
RUN ./mvnw -B -pl ${MODULE} -am -DskipTests package

# ── Runtime stage ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

ARG MODULE
ARG APP_PORT=8080

WORKDIR /app

# Run as non-root
RUN groupadd -r skyline && useradd -r -g skyline -s /bin/false skyline
RUN chown skyline:skyline /app
USER skyline

# Copy the fat jar from the build stage
COPY --from=build --chown=skyline:skyline /workspace/${MODULE}/target/*.jar app.jar

EXPOSE ${APP_PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
