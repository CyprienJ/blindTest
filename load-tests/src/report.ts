import type { GameResult } from "./game-client.js";
import type { MetricSummary } from "./metrics.js";

export interface LatencyStats {
  count: number;
  p50: number;
  p95: number;
  max: number;
}

export function latencyStats(valuesMs: number[]): LatencyStats {
  if (valuesMs.length === 0) {
    return { count: 0, p50: 0, p95: 0, max: 0 };
  }
  const sorted = [...valuesMs].sort((a, b) => a - b);
  const percentile = (p: number): number => sorted[Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
  return { count: sorted.length, p50: percentile(0.5), p95: percentile(0.95), max: sorted.at(-1)! };
}

export interface ScenarioReport {
  name: string;
  gameCount: number;
  gamesOk: number;
  wallClockMs: number;
  terminalLatency: LatencyStats;
  errors: string[];
  server?: Record<string, MetricSummary | undefined>;
  harnessCpuPercent?: MetricSummary;
  harnessEventLoopDelayP99Ms?: MetricSummary;
}

export function summarizeGames(name: string, results: GameResult[], wallClockMs: number): ScenarioReport {
  const terminalLatencies = results.flatMap((result) => result.rounds.map((round) => round.terminalLatencyMs));
  const errors = results.flatMap((result) => result.errors.map((error) => `${result.sessionId}: ${error}`));
  return {
    name,
    gameCount: results.length,
    gamesOk: results.filter((result) => result.ok).length,
    wallClockMs,
    terminalLatency: latencyStats(terminalLatencies),
    errors,
  };
}

function fmtMs(value: number): string {
  return `${Math.round(value)}ms`;
}

export function printReport(reports: ScenarioReport[]): void {
  console.log("\n=== Load and concurrency smoke test report ===\n");
  for (const report of reports) {
    console.log(`--- ${report.name} ---`);
    console.log(
      `games: ${report.gamesOk}/${report.gameCount} ok, wall clock ${fmtMs(report.wallClockMs)}`,
    );
    console.log(
      `round terminal latency: p50=${fmtMs(report.terminalLatency.p50)} `
        + `p95=${fmtMs(report.terminalLatency.p95)} max=${fmtMs(report.terminalLatency.max)} `
        + `(n=${report.terminalLatency.count})`,
    );
    if (report.server) {
      for (const [name, summary] of Object.entries(report.server)) {
        if (summary) {
          console.log(
            `  server ${name}: min=${summary.min.toFixed(1)} avg=${summary.avg.toFixed(1)} max=${summary.max.toFixed(1)}`,
          );
        }
      }
    }
    if (report.harnessCpuPercent) {
      console.log(
        `  harness cpu%: avg=${report.harnessCpuPercent.avg.toFixed(1)} max=${report.harnessCpuPercent.max.toFixed(1)}`,
      );
    }
    if (report.harnessEventLoopDelayP99Ms) {
      console.log(
        `  harness event-loop p99 delay: avg=${report.harnessEventLoopDelayP99Ms.avg.toFixed(1)}ms `
          + `max=${report.harnessEventLoopDelayP99Ms.max.toFixed(1)}ms`,
      );
    }
    if (report.errors.length > 0) {
      console.log(`  ${report.errors.length} error(s):`);
      for (const error of report.errors.slice(0, 20)) {
        console.log(`    - ${error}`);
      }
      if (report.errors.length > 20) {
        console.log(`    ... and ${report.errors.length - 20} more`);
      }
    }
    console.log("");
  }
  console.log("=== Full JSON report ===");
  console.log(JSON.stringify(reports, null, 2));
}
