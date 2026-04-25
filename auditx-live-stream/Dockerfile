# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy root pom and all module poms first (layer caching)
COPY pom.xml .
COPY auditx-common/pom.xml auditx-common/
COPY auditx-live-stream/pom.xml auditx-live-stream/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl auditx-common,auditx-live-stream -am -q

# Copy source
COPY auditx-common/src auditx-common/src
COPY auditx-live-stream/src auditx-live-stream/src

# Build
RUN mvn package -pl auditx-common,auditx-live-stream -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S auditx && adduser -S auditx -G auditx
COPY --from=build /workspace/auditx-live-stream/target/*.jar app.jar
USER auditx
ENTRYPOINT ["java", "-jar", "app.jar"]
