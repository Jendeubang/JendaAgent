FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package spring-boot:repackage

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system jenda && useradd --system --gid jenda --home /app jenda
COPY --from=builder /workspace/target/genie-backend-0.0.1-SNAPSHOT.jar /app/jenda-agent.jar
RUN chown jenda:jenda /app/jenda-agent.jar
USER jenda
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/jenda-agent.jar"]
