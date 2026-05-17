# Multi-stage build for NYX Jasper Creator
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-11 AS builder

WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Create runtime image
FROM eclipse-temurin:11-jre

WORKDIR /app

# Create necessary directories
RUN mkdir -p /app/reports /app/jasperdef

# Copy the built JAR from builder stage
COPY --from=builder /app/target/nyx-jasper-creator-1.0.0.jar /app/nyx-jasper-creator.jar

# Environment variables with default values
ENV ACTIVEMQ_BROKER_URL=tcp://localhost:61616 \
    ACTIVEMQ_USERNAME=admin \
    ACTIVEMQ_PASSWORD=admin \
    ACTIVEMQ_QUEUE_NAME=nyx.jasper.queue \
    ACTIVEMQ_UPLOAD_QUEUE_NAME=JASPER_UPLOAD \
    ACTIVEMQ_STATUS_TOPIC=RPN_MODULE_INFO \
    STATUS_INTERVAL_SECONDS=5 \
    MODULE_NAME=NYX_Jasper_Creator \
    MODULE_VERSION=1.0.0 \
    JASPER_OUTPUT_DIRECTORY=reports

# Expose any ports if needed (optional)
# EXPOSE 8080

# Set the entrypoint
ENTRYPOINT ["java", "-jar", "/app/nyx-jasper-creator.jar"]

# Health check (optional - checks if process is running)
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD pgrep -f nyx-jasper-creator.jar || exit 1
