function envInt(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

// The known fixed title FakeProcessFactory writes into every imported track's metadata
// under the e2e profile (backend/src/main/java/com/blindtest/testing/e2e/FakeProcessFactory.java).
// Every track in the seeded catalogue shares this title, which is what makes a single known
// "correct answer" string usable across every virtual player and every track.
export const KNOWN_TRACK_TITLE = "Deterministic e2e track title";

// Cloudflare's documented "always passes" Turnstile test response token — fixed by design so
// automated clients can skip loading the real widget. Valid only paired with the matching test
// secret key (docker-compose.test.yml sets CLOUDFLARE_TURNSTILE_SECRET_KEY to the paired value).
export const DUMMY_TURNSTILE_TOKEN = "XXXX.DUMMY.TOKEN.XXXX";

export const config = {
  // Traffic that should look like a real browser goes through nginx, exactly like the
  // Playwright full-stack suite (frontend/e2e-full-stack) does.
  apiBaseUrl: process.env.API_BASE_URL ?? "http://frontend",
  // /actuator/metrics/** is not proxied by the frontend's nginx config
  // (frontend/nginx.conf only forwards /actuator/health) — read it straight from the backend.
  metricsBaseUrl: process.env.METRICS_BASE_URL ?? "http://backend:8080",

  catalogueTrackCount: envInt("CATALOGUE_TRACK_COUNT", 30),

  concurrentGames: envInt("CONCURRENT_GAMES", 10),
  headroomGames: envInt("HEADROOM_GAMES", 40),
  importIsolationJobCount: envInt("IMPORT_ISOLATION_JOB_COUNT", 5),

  // Real product defaults (ADR-0008) for the single-game baseline measurement.
  baselineTrackCount: envInt("BASELINE_TRACK_COUNT", 15),
  baselineRoundSeconds: envInt("BASELINE_ROUND_SECONDS", 30),

  // ADR-0008 minimums for the concurrency/headroom/import-isolation scenarios, so a run of
  // 40 games completes in a reasonable wall-clock time while still exercising real
  // deadline-driven auto-reveal (not just instant answers).
  loadTrackCount: envInt("LOAD_TRACK_COUNT", 5),
  loadRoundSeconds: envInt("LOAD_ROUND_SECONDS", 10),

  commandTimeoutMs: envInt("COMMAND_TIMEOUT_MS", 8_000),

  metricsSampleIntervalMs: envInt("METRICS_SAMPLE_INTERVAL_MS", 1_000),
};
