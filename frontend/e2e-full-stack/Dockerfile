# Runs the full-stack Playwright suite (playwright.full-stack.config.ts) against the isolated
# Compose stack defined in ../../docker-compose.test.yml. Built by the `tests` service there.
FROM node:22-bookworm-slim
WORKDIR /app

COPY package*.json ./
RUN npm ci --prefer-offline

COPY . .
RUN npx playwright install --with-deps chromium

CMD ["npx", "playwright", "test", "--config=playwright.full-stack.config.ts"]
