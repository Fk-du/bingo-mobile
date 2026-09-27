# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependency downloads before copying sources
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# No Maven wrapper needed: the maven:3.9 base image already provides `mvn`,
# which is what the package step below actually calls. Copying mvnw/.mvn here
# only forced the build to carry a wrapper directory it never used.
COPY src ./src

RUN mvn -B -q -Dmaven.test.skip=true package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Payment screenshots are stored locally -> keep them on a persistent volume.
RUN mkdir -p /data/screenshots && chmod 755 /data/screenshots

ENV TZ=UTC \
    SCREENSHOTS_DIR=/data/screenshots \
    JAVA_TOOL_OPTIONS="-Xms256m -Xmx1024m"

EXPOSE 8080
# A Spring Boot start on an empty database takes ~30s (Hibernate builds the
# schema, the tenant migrations run), so the start period has to outlast that
# or the probe exhausts its retries mid-boot. start-interval probes every 5s
# during the start period, so the container goes healthy within seconds of the
# app answering instead of waiting a full interval. The compose file repeats
# this healthcheck so it also applies when the image is run from the stack.
HEALTHCHECK --interval=10s --timeout=5s --start-period=180s --start-interval=5s --retries=6 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]