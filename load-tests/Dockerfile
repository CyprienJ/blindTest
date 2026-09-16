# Headless REST+STOMP load-test runner (E2E-05). No browser: talks the real protocol
# directly, so the image stays small and many virtual players stay cheap to run.
FROM node:22-bookworm-slim
WORKDIR /app

COPY package*.json ./
RUN npm ci --prefer-offline

COPY . .

CMD ["npm", "start"]
