# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy root pom and all module poms first (for layer caching)
COPY pom.xml .
COPY auditx-common/pom.xml auditx-common/
COPY auditx-gateway/pom.xml auditx-gateway/
COPY auditx-ingestion/pom.xml auditx-ingestion/
COPY auditx-parser/pom.xml auditx-parser/
COPY auditx-risk-engine/pom.xml auditx-risk-engine/
COPY auditx-llm/pom.xml auditx-llm/
COPY auditx-alert/pom.xml auditx-alert/
COPY auditx-report/pom.xml auditx-report/
COPY auditx-sdk/pom.xml auditx-sdk/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -pl auditx-common,auditx-alert -am -q

# Copy source
COPY auditx-common/src auditx-common/src
COPY auditx-alert/src auditx-alert/src

# Build
RUN mvn package -pl auditx-common,auditx-alert -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S auditx && adduser -S auditx -G auditx
COPY --from=build /workspace/auditx-alert/target/*.jar app.jar
USER auditx
ENTRYPOINT ["java", "-jar", "app.jar"]
