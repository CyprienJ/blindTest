# ── Build stage ────────────────────────────────────────────────────────
FROM node:22.22.3-alpine3.22 AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --prefer-offline

COPY . .
ARG ANGULAR_CONFIGURATION=production
RUN npx ng build --configuration "${ANGULAR_CONFIGURATION}"

# ── Serve stage ─────────────────────────────────────────────────────────
FROM nginx:1.27.5-alpine

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Media files are mounted at runtime via the shared volume
VOLUME ["/media"]

# Nginx serves the application without root privileges. The runtime Compose
# configuration mounts the cache and temporary paths as tmpfs when read-only
# operation is enabled.
RUN mkdir -p /var/cache/nginx /tmp \
    && sed -i -e '/^user /d' -e 's#^pid .*#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx /tmp /usr/share/nginx/html

USER nginx

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1

ENTRYPOINT ["nginx", "-g", "daemon off;"]
