# ── Build stage ────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies before copying source
COPY pom.xml checkstyle.xml ./
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Runtime stage ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN mkdir -p /media

COPY --from=build /app/target/blindtest-backend-*.jar app.jar

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
