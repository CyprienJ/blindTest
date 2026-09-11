# Blindtest frontend

The Angular 21 application uses standalone components and lazy feature routes.
Start with the [project overview](../README.md), then the
[frontend guide](../docs/frontend/README.md) for routes, state, and current behavior.
The [gameplay client guide](../docs/frontend/gameplay.md) covers STOMP and audio.

## Local development

From this directory, with the backend running on port 8080:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. The development proxy forwards `/api`, `/media`, and
`/ws` to the backend. See [local setup](../docs/development/README.md) for the full
stack and [testing](../docs/testing.md) for lint, build, Karma, and Playwright.

Use the [UI reference](../docs/Blindtest/Blindtest%20Views.html) and adjacent JSX
when implementing screens. [Current status](../docs/product/status.md) distinguishes
implemented features from placeholders; the [roadmap](../docs/plan.md) owns scope
and ticket acceptance.
