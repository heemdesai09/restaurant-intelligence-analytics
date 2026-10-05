# -------------------------------------------------------------
# Stage 1: Build the Scala standalone application using sbt
# -------------------------------------------------------------
FROM sbtscala/scala-sbt:eclipse-temurin-17.0.4_1.7.1_3.2.0 AS builder

WORKDIR /workspace

# Copy build definition files first to leverage Docker layer caching
COPY backend/project /workspace/backend/project
COPY backend/build.sbt /workspace/backend/build.sbt

# Fetch dependencies
RUN cd /workspace/backend && sbt update

# Copy source code and build standalone assembly JAR
COPY backend/src /workspace/backend/src
RUN cd /workspace/backend && sbt assembly

# -------------------------------------------------------------
# Stage 2: Minimal production runtime container
# -------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built assembly JAR from builder stage
COPY --from=builder /workspace/backend/target/scala-3.3.3/app.jar /app/app.jar

# Copy frontend web assets
COPY frontend /app/frontend

# Default environment configuration
EXPOSE 8080
ENV PORT=8080

# Launch application in Web Server mode
CMD ["java", "-jar", "app.jar", "web"]
