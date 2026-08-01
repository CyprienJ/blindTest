# ── Build stage ────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --prefer-offline

COPY . .
ARG ANGULAR_CONFIGURATION=production
RUN npx ng build --configuration "${ANGULAR_CONFIGURATION}"

# ── Serve stage ─────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Media files are mounted at runtime via the shared volume
VOLUME ["/media"]
