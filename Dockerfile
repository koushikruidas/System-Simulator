# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM gradle:8.12-jdk17 AS builder

WORKDIR /app

# Copy dependency descriptors first — layer is cached until these change
COPY build.gradle settings.gradle ./
COPY gradle gradle/
COPY gradlew ./

# Warm the Gradle dependency cache
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build the fat JAR (skip tests — handled in CI)
COPY src src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/libs/systemSimulator-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
