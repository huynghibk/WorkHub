# Build fat distribution of the Ktor server
FROM gradle:8.6-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :server:installDist --no-daemon -x test

# Runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/server/build/install/server /app
ENV PORT=8080
ENV WORKHUB_DATA_DIR=/data
ENV WORKHUB_JWT_SECRET=change-me-in-production
RUN mkdir -p /data
EXPOSE 8080
CMD ["/app/bin/server"]
