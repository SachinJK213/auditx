# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

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
COPY auditx-policy-engine/pom.xml auditx-policy-engine/
COPY auditx-compliance/pom.xml auditx-compliance/
COPY auditx-live-stream/pom.xml auditx-live-stream/
COPY auditx-pii-detector/pom.xml auditx-pii-detector/
COPY auditx-enrichment/pom.xml auditx-enrichment/
COPY auditx-notification/pom.xml auditx-notification/

RUN mvn dependency:go-offline -pl auditx-common,auditx-pii-detector -am -q

COPY auditx-common/src auditx-common/src
COPY auditx-pii-detector/src auditx-pii-detector/src

RUN mvn package -pl auditx-common,auditx-pii-detector -am -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S auditx && adduser -S auditx -G auditx
COPY --from=build /workspace/auditx-pii-detector/target/*.jar app.jar
USER auditx
ENTRYPOINT ["java", "-jar", "app.jar"]
