# Multi-agent system (Jason 3.3 on Java 21).
# Stage 1 compiles on the build machine's own CPU, even when the target is arm64,
# so Gradle never runs under emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version
COPY . .
RUN ./gradlew --no-daemon jar copyRuntimeLibs

# Stage 2: small runtime image for the target platform.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/fleet-mas-env.jar /app/lib/
COPY --from=build /src/build/deps/ /app/lib/
COPY --from=build /src/mas /app/mas
COPY --from=build /src/data /app/data
COPY --from=build /src/logging.properties /app/
ENV IOT_BACKEND_URL=http://iot-control-unit:3002
CMD ["java", "-Djava.awt.headless=true", "-Djava.util.logging.config.file=logging.properties", \
     "-cp", "lib/*", "jason.infra.local.RunLocalMAS", "mas/maintenance.mas2j"]
