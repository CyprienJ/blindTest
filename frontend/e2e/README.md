# Playwright end-to-end tests

## Browsers

- **Chromium** is required on every push (`npm run test:e2e`, wired into
  `.github/workflows/ci.yml`).
- **Firefox** and **WebKit** are configured (`playwright.config.ts`) and run
  the full suite nightly via `.github/workflows/e2e-cross-browser.yml`
  (`npm run test:e2e:all-browsers`), not on every push. Promote them to
  required once they have proven stable over time.

## Accessibility and visual coverage (`accessibility.spec.ts`, `visual.spec.ts`)

- Keyboard reachability and a visible focus indicator are checked on the
  sign-in form, the public home hero actions, and the in-game answer field.
- Semantic labels and live regions (`role`, `aria-live`, `aria-atomic`,
  `aria-describedby`) are asserted directly rather than through a full
  automated audit tool.
- A small viewport (360×740) check only asserts core screens render without a
  page error — mobile *gameplay quality* is explicitly out of scope for V1
  (see `docs/roadmap/lot-6-frontend.md#ui-10`).
- Visual snapshots are intentionally limited to a couple of screens with no
  timers, animation, or server-driven content (public home, sign-in), run on
  Chromium only, since pixel baselines are engine/OS-specific. Regenerate
  with `npx playwright test e2e/visual.spec.ts --project=chromium --update-snapshots`
  after an intentional visual change.
