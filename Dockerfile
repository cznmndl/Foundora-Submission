# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Resolve dependencies first so they are cached between builds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run locally / in CI; skip them here to keep image builds fast.
RUN mvn -B -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 1001 foundora
COPY --from=build /app/target/foundora-*.jar app.jar
USER foundora

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Documentation only: Render injects PORT and the app binds to it.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
