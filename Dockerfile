# Build only shared + server (no Android SDK required)
FROM gradle:8.6-jdk17 AS build
WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew gradlew.bat gradle.properties ./
COPY settings-server.gradle.kts ./settings.gradle.kts
COPY build-server.gradle.kts ./build.gradle.kts
COPY shared ./shared
COPY server ./server

RUN chmod +x gradlew \
    && ./gradlew :server:installDist --no-daemon --stacktrace -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app
ENV PORT=8080
ENV WORKHUB_DATA_DIR=/data
ENV WORKHUB_JWT_SECRET=change-me-in-production
RUN mkdir -p /data
EXPOSE 8080
CMD ["/app/bin/server"]
