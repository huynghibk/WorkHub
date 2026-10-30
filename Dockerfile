# Build only shared + server (no Android SDK required)
FROM gradle:8.6-jdk17 AS build
WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew gradlew.bat gradle.properties ./
COPY settings-server.gradle.kts ./settings.gradle.kts
COPY build-server.gradle.kts ./build.gradle.kts
COPY shared ./shared
COPY server ./server

# Render free builders are memory-tight; keep Gradle heap modest.
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8"
# On failure, scroll ABOVE the Java stacktrace for lines starting with "e: file://" (real Kotlin errors).
RUN chmod +x gradlew \
    && ./gradlew :server:installDist --no-daemon --stacktrace -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app
# Không set PORT ở đây — Render tự inject (thường 10000).
ENV WORKHUB_DATA_DIR=/data
ENV WORKHUB_JWT_SECRET=change-me-in-production
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseContainerSupport"
RUN mkdir -p /data \
    && printf '%s\n' \
      '#!/bin/sh' \
      'set -e' \
      'PORT="${PORT:-8080}"' \
      'echo "WorkHub starting on 0.0.0.0:${PORT}"' \
      'exec java $JAVA_TOOL_OPTIONS -cp "/app/lib/*" com.workhub.server.ApplicationKt' \
      > /app/start.sh \
    && chmod +x /app/start.sh
EXPOSE 8080
CMD ["/app/start.sh"]
