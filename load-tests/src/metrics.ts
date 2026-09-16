import { monitorEventLoopDelay } from "node:perf_hooks";
import { readMetric } from "./api-client.js";
import { config } from "./config.js";

const SERVER_METRICS = [
  "jvm.memory.used",
  "jvm.threads.live",
  "hikaricp.connections.active",
  "hikaricp.connections.pending",
  "tomcat.threads.busy",
] as const;

type MetricName = (typeof SERVER_METRICS)[number];

export interface MetricSample {
  atMs: number;
  server: Partial<Record<MetricName, number>>;
  harnessCpuPercent: number;
  harnessEventLoopDelayP99Ms: number;
}

export interface MetricSummary {
  min: number;
  max: number;
  avg: number;
}

function summarize(values: number[]): MetricSummary {
  if (values.length === 0) {
    return { min: 0, max: 0, avg: 0 };
  }
  return {
    min: Math.min(...values),
    max: Math.max(...values),
    avg: values.reduce((sum, value) => sum + value, 0) / values.length,
  };
}

/** Samples backend Actuator metrics and the harness's own CPU/event-loop-lag on an interval,
 * so a measured latency spike can be attributed to the server or ruled out as harness overhead
 * (this Node process drives dozens of concurrent virtual players from one event loop). */
export class MetricsRecorder {
  private readonly samples: MetricSample[] = [];
  private timer: NodeJS.Timeout | undefined;
  private lastCpuUsage = process.cpuUsage();
  private lastSampleAtMs = Date.now();
  private readonly eventLoopHistogram = monitorEventLoopDelay({ resolution: 10 });

  constructor(private readonly adminToken: string) {
    this.eventLoopHistogram.enable();
  }

  start(): void {
    this.timer = setInterval(() => {
      void this.sampleOnce();
    }, config.metricsSampleIntervalMs);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
    this.eventLoopHistogram.disable();
  }

  private async sampleOnce(): Promise<void> {
    const now = Date.now();
    const server: Partial<Record<MetricName, number>> = {};
    await Promise.all(
      SERVER_METRICS.map(async (name) => {
        const value = await readMetric(this.adminToken, name).catch(() => undefined);
        if (value !== undefined) {
          server[name] = value;
        }
      }),
    );

    const cpuUsage = process.cpuUsage();
    const elapsedMs = now - this.lastSampleAtMs;
    const cpuMs = (cpuUsage.user - this.lastCpuUsage.user + cpuUsage.system - this.lastCpuUsage.system) / 1000;
    const harnessCpuPercent = elapsedMs > 0 ? (100 * cpuMs) / elapsedMs : 0;
    this.lastCpuUsage = cpuUsage;
    this.lastSampleAtMs = now;

    this.samples.push({
      atMs: now,
      server,
      harnessCpuPercent,
      harnessEventLoopDelayP99Ms: this.eventLoopHistogram.percentile(99) / 1_000_000,
    });
  }

  samplesBetween(startMs: number, endMs: number): MetricSample[] {
    return this.samples.filter((sample) => sample.atMs >= startMs && sample.atMs <= endMs);
  }

  summarizeWindow(startMs: number, endMs: number): {
    server: Partial<Record<MetricName, MetricSummary>>;
    harnessCpuPercent: MetricSummary;
    harnessEventLoopDelayP99Ms: MetricSummary;
  } {
    const windowSamples = this.samplesBetween(startMs, endMs);
    const server: Partial<Record<MetricName, MetricSummary>> = {};
    for (const name of SERVER_METRICS) {
      const values = windowSamples
        .map((sample) => sample.server[name])
        .filter((value): value is number => value !== undefined);
      server[name] = summarize(values);
    }
    return {
      server,
      harnessCpuPercent: summarize(windowSamples.map((sample) => sample.harnessCpuPercent)),
      harnessEventLoopDelayP99Ms: summarize(windowSamples.map((sample) => sample.harnessEventLoopDelayP99Ms)),
    };
  }
}
