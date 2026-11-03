# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# `dependency:go-offline` was tried as a separate caching layer but proved slow/unreliable
# against this dependency graph (Testcontainers BOM in particular) — a single build step is
# simpler and only loses the fine-grained "deps vs. code" cache split, not correctness.
COPY pom.xml .
COPY src/main ./src/main
RUN mvn -q -B -Dmaven.test.skip=true package && \
    mv target/*.jar target/app.jar

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S platform && adduser -S platform -G platform
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
RUN chown platform:platform app.jar
USER platform

ENV JAVA_OPTS=""
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
