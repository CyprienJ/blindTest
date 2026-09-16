import { playGame, type GameResult } from "./game-client.js";
import type { MetricsRecorder } from "./metrics.js";
import { summarizeGames, type ScenarioReport } from "./report.js";
import { importAdditionalTracks, type Catalogue } from "./catalogue.js";
import { config } from "./config.js";

function attachServerMetrics(report: ScenarioReport, metrics: MetricsRecorder, startMs: number, endMs: number): ScenarioReport {
  const window = metrics.summarizeWindow(startMs, endMs);
  return {
    ...report,
    server: window.server,
    harnessCpuPercent: window.harnessCpuPercent,
    harnessEventLoopDelayP99Ms: window.harnessEventLoopDelayP99Ms,
  };
}

export async function runBaseline(
  playerToken: string,
  catalogue: Catalogue,
  metrics: MetricsRecorder,
): Promise<ScenarioReport> {
  const startMs = Date.now();
  const result = await playGame(playerToken, catalogue.playlistId, config.baselineTrackCount, config.baselineRoundSeconds);
  return attachServerMetrics(summarizeGames("1. Baseline (single game, real defaults)", [result], Date.now() - startMs), metrics, startMs, Date.now());
}

export async function runConcurrentGames(
  name: string,
  playerTokens: string[],
  catalogue: Catalogue,
  metrics: MetricsRecorder,
): Promise<{ report: ScenarioReport; results: GameResult[] }> {
  const startMs = Date.now();
  const results = await Promise.all(
    playerTokens.map((token) =>
      playGame(token, catalogue.playlistId, config.loadTrackCount, config.loadRoundSeconds),
    ),
  );
  const report = attachServerMetrics(summarizeGames(name, results, Date.now() - startMs), metrics, startMs, Date.now());
  return { report, results };
}

/** Fires concurrent games alongside concurrent catalogue imports into the same shared
 * playlist, and asserts every in-flight game still completes with exactly the round count
 * requested at creation — proving a concurrent catalogue mutation cannot perturb an
 * already-created game's configuration (ADR-0008: configuration is snapshotted at creation). */
export async function runImportIsolation(
  playerTokens: string[],
  catalogue: Catalogue,
  metrics: MetricsRecorder,
): Promise<ScenarioReport> {
  const startMs = Date.now();
  const gamesPromise = Promise.all(
    playerTokens.map((token) =>
      playGame(token, catalogue.playlistId, config.loadTrackCount, config.loadRoundSeconds),
    ),
  );
  const importsPromise = importAdditionalTracks(
    catalogue,
    config.importIsolationJobCount,
    catalogue.trackCount + 1_000,
  );
  const [results] = await Promise.all([gamesPromise, importsPromise]);

  const errors = results.flatMap((result) => result.errors.map((error) => `${result.sessionId}: ${error}`));
  for (const result of results) {
    if (result.rounds.length !== config.loadTrackCount) {
      errors.push(
        `${result.sessionId}: expected ${config.loadTrackCount} rounds despite concurrent import, got ${result.rounds.length}`,
      );
    }
  }
  const report = summarizeGames("5. Import isolation (concurrent imports during games)", results, Date.now() - startMs);
  return attachServerMetrics({ ...report, errors: [...report.errors, ...errors] }, metrics, startMs, Date.now());
}
