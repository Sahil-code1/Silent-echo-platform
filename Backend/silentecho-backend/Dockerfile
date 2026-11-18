# Multi-stage build. Maven builds the Spring Boot JAR; the runtime image contains only the JRE.
# Tests are not compiled here. Run ./mvnw clean verify before deploying.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod \
    SILENTECHO_EVIDENCE_DIR=/app/data/evidence
RUN mkdir -p /app/data/evidence
COPY --from=build /workspace/target/silentecho-backend-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
