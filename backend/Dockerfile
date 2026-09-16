# ── Build stage ────────────────────────────────────────────────────────
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies before copying source
COPY pom.xml checkstyle.xml ./
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Runtime dependencies ───────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine-3.22 AS runtime-dependencies

ARG YT_DLP_VERSION=2026.08.19
ARG FFMPEG_VERSION=6.1.2-r2

RUN apk add --no-cache "ffmpeg=${FFMPEG_VERSION}" python3 py3-pip \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir "yt-dlp==${YT_DLP_VERSION}" \
    && test "$(/opt/yt-dlp/bin/yt-dlp --version)" = "${YT_DLP_VERSION}" \
    && ffmpeg -version | grep -F "ffmpeg version 6.1.2" >/dev/null

ENV PATH="/opt/yt-dlp/bin:${PATH}"

# ── Runtime stage ───────────────────────────────────────────────────────
FROM runtime-dependencies AS runtime
WORKDIR /app

RUN addgroup -S blindtest \
    && adduser -S -D -H -G blindtest blindtest \
    && mkdir -p /media /tmp/blindtest-imports \
    && chown -R blindtest:blindtest /app /media /tmp/blindtest-imports

COPY --from=build --chown=blindtest:blindtest /app/target/blindtest-backend-*.jar app.jar

USER blindtest

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
