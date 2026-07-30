# Blindtest frontend

This Angular 21 application uses standalone components and lazy feature routes.

## Current status

The login, registration, email-verification, forgot-password, and password-reset
flows are implemented, together with the auth service, route guard, JWT
interceptor, refresh flow, local session state, and authenticated sign-out control.

The lobby, game, end screen, profile, friends, admin, and notification components
are route scaffolds only. Their presence does not mean those features are
implemented. See the root [`README.md`](../README.md) for the repository-wide
status and [`docs/plan.md`](../docs/plan.md) for the authoritative delivery scope.

## Development

Install the pinned dependencies and start the Angular development server:

```bash
npm ci
npm start
```

Open `http://localhost:4200`. The development proxy forwards `/api`, `/media`, and `/ws`
to `http://localhost:8080`.

## Validation

```bash
npm run lint
npm run build
npm run test -- --watch=false --browsers=ChromeHeadless
npm run test:e2e
```

Install the Playwright-managed Chromium before the first end-to-end run:

```bash
npx playwright install chromium
```

`test:e2e:ui` opens Playwright's interactive runner and `test:e2e:debug` starts
the debugger. Reports are written to `playwright-report/`; failure traces,
screenshots, and videos are written to `test-results/`.

The Playwright authentication suite mocks every `/api/auth` response and the
Turnstile browser API. Its shared fixture blocks third-party traffic and rejects
unexpected API requests, so the tests require neither the backend nor Cloudflare
or Resend network access.

On the project workstation, tests may require:

```bash
CHROME_BIN=/snap/bin/chromium npm run test -- --watch=false --browsers=ChromeHeadless
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/snap/bin/chromium npm run test:e2e
```

## UI reference

When implementing a feature, use
[`docs/Blindtest/Blindtest Views.html`](../docs/Blindtest/Blindtest%20Views.html)
and its adjacent JSX sources as the visual reference. Product scope and executable
code still take precedence over the reference screens.
