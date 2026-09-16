import { randomUUID } from "node:crypto";
import { Client, type IMessage } from "@stomp/stompjs";
import WebSocket from "ws";
import { config, KNOWN_TRACK_TITLE } from "./config.js";
import { awaitSummary, createSoloSession, type GameSummary } from "./api-client.js";

// Mirrors backend/src/main/java/com/blindtest/websocket/protocol/SessionCommandType.java
type CommandType = "START_GAME" | "SUBMIT_ANSWER" | "SKIP_ROUND" | "ADVANCE_ROUND" | "REQUEST_SNAPSHOT";

// Round strategies, cycled across a game's rounds so a single run exercises every terminal
// path (title found early, title found late for a lower score, an incorrect guess that then
// times out, and an explicit skip) rather than only ever taking the fast path.
export type RoundStrategy = "immediate_correct" | "late_correct" | "wrong_then_timeout" | "skip";
export const ROUND_STRATEGIES: RoundStrategy[] = [
  "immediate_correct",
  "late_correct",
  "wrong_then_timeout",
  "skip",
];

interface PublicEvent {
  metadata: { sessionVersion: number; occurredAt: string };
  type: "SESSION_READY" | "ROUND_PREPARING" | "ROUND_STARTED" | "ROUND_REVEALED"
    | "SCORE_UPDATED" | "GAME_FINISHED";
  payload: Record<string, unknown>;
}

interface PrivateEvent {
  metadata: { sessionVersion: number; occurredAt: string };
  type: "SECONDARY_MATCH" | "ANSWER_REJECTED" | "CURRENT_SNAPSHOT" | "ERROR";
  payload: Record<string, unknown>;
}

class EventBuffer<T> {
  private readonly buffer: T[] = [];
  private readonly waiters: { predicate: (item: T) => boolean; resolve: (item: T) => void }[] = [];

  push(item: T): void {
    this.buffer.push(item);
    for (let index = this.waiters.length - 1; index >= 0; index -= 1) {
      if (this.waiters[index].predicate(item)) {
        this.waiters[index].resolve(item);
        this.waiters.splice(index, 1);
      }
    }
  }

  all(predicate: (item: T) => boolean): T[] {
    return this.buffer.filter(predicate);
  }

  async waitFor(predicate: (item: T) => boolean, timeoutMs: number, label: string): Promise<T> {
    const existing = this.buffer.find(predicate);
    if (existing) {
      return existing;
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        const index = this.waiters.findIndex((waiter) => waiter.resolve === wrapped);
        if (index >= 0) {
          this.waiters.splice(index, 1);
        }
        reject(new Error(`Timed out after ${timeoutMs}ms waiting for ${label}`));
      }, timeoutMs);
      const wrapped = (item: T): void => {
        clearTimeout(timer);
        resolve(item);
      };
      this.waiters.push({ predicate, resolve: wrapped });
    });
  }
}

export interface RoundResult {
  roundIndex: number;
  strategy: RoundStrategy;
  expectedOutcome: "TITLE_FOUND" | "TIMEOUT" | "SKIPPED";
  actualOutcome?: string;
  points?: number;
  scoreWithinExpectedRange?: boolean;
  terminalLatencyMs: number;
}

export interface GameResult {
  sessionId: string;
  ok: boolean;
  errors: string[];
  rounds: RoundResult[];
  privateErrorCount: number;
  wallClockMs: number;
  finalScore: number;
  summary?: GameSummary;
  summaryReconciled: boolean;
}

function wsUrl(httpBase: string, path: string): string {
  const url = new URL(path, httpBase);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  return url.toString();
}

/** Expected points for a correct answer accepted `elapsedMs` into a `roundDurationMs` round,
 * per ADR-0007 (docs/adr/0007-title-score-rounding-and-minimum.md). Client-measured elapsed
 * time is a lower bound on the server's authoritative elapsed time (network latency only adds
 * delay before the server timestamps acceptance), so this is used as an upper bound on score. */
function expectedScoreUpperBound(elapsedMs: number, roundDurationMs: number): number {
  const remaining = Math.max(0, roundDurationMs - elapsedMs);
  const linear = Math.min(1000, Math.max(0, (1000 * remaining) / roundDurationMs));
  return Math.max(100, Math.round(linear));
}

/** Plays one full solo game headlessly over real REST + STOMP. Never throws for gameplay-level
 * problems (a rejected assertion, an error event, a timeout) — those are recorded in the
 * returned result's `errors`/`ok` fields so a batch of concurrent games can all finish and be
 * reported together; it only throws for setup failures (session creation, connect failure). */
export async function playGame(
  token: string,
  playlistId: string,
  trackCount: number,
  roundDurationSeconds: number,
): Promise<GameResult> {
  const startedAt = Date.now();
  const session = await createSoloSession(token, playlistId, trackCount, roundDurationSeconds);
  const roundDurationMs = roundDurationSeconds * 1000;

  const publicEvents = new EventBuffer<{ event: PublicEvent; receivedAt: number }>();
  const privateEvents = new EventBuffer<{ event: PrivateEvent; receivedAt: number }>();
  let sessionVersion = session.version;
  const errors: string[] = [];

  const client = new Client({
    webSocketFactory: () =>
      new WebSocket(wsUrl(config.apiBaseUrl, session.connection.webSocketEndpoint)) as unknown as globalThis.WebSocket,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 0,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
  });
  client.debug = () => {};
  client.onWebSocketError = (event) => errors.push(`websocket error: ${String(event)}`);
  client.onStompError = (frame) => errors.push(`stomp error: ${frame.headers.message ?? frame.body}`);

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("STOMP connect timed out")), config.commandTimeoutMs);
    client.onConnect = () => {
      clearTimeout(timer);
      client.subscribe(session.connection.eventDestination, (message: IMessage) => {
        const event = JSON.parse(message.body) as PublicEvent;
        sessionVersion = Math.max(sessionVersion, event.metadata.sessionVersion);
        publicEvents.push({ event, receivedAt: Date.now() });
      });
      client.subscribe(session.connection.privateDestination, (message: IMessage) => {
        const event = JSON.parse(message.body) as PrivateEvent;
        sessionVersion = Math.max(sessionVersion, event.metadata.sessionVersion);
        privateEvents.push({ event, receivedAt: Date.now() });
      });
      resolve();
    };
    client.onWebSocketError = (event) => {
      clearTimeout(timer);
      reject(new Error(`websocket error during connect: ${String(event)}`));
    };
    client.activate();
  });

  function send(type: CommandType, answer?: string): void {
    client.publish({
      destination: session.connection.commandDestination,
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        protocolVersion: 1,
        commandId: randomUUID(),
        expectedSessionVersion: sessionVersion,
        type,
        answer: answer ?? null,
      }),
    });
  }

  const rounds: RoundResult[] = [];
  try {
    send("START_GAME");
    for (let roundIndex = 0; roundIndex < trackCount; roundIndex += 1) {
      const strategy = ROUND_STRATEGIES[roundIndex % ROUND_STRATEGIES.length];
      const started = await publicEvents.waitFor(
        (item) => item.event.type === "ROUND_STARTED" && item.event.payload.roundIndex === roundIndex,
        config.commandTimeoutMs,
        `ROUND_STARTED[${roundIndex}]`,
      );
      const startsAtMs = Date.parse(started.event.payload.startsAt as string);
      const expectedOutcome: RoundResult["expectedOutcome"] =
        strategy === "skip" ? "SKIPPED" : strategy === "wrong_then_timeout" ? "TIMEOUT" : "TITLE_FOUND";

      let answerSentAt: number | null = null;
      if (strategy === "immediate_correct") {
        answerSentAt = Date.now();
        send("SUBMIT_ANSWER", KNOWN_TRACK_TITLE);
      } else if (strategy === "late_correct") {
        const delay = Math.max(0, roundDurationMs * 0.8 - (Date.now() - startsAtMs));
        await new Promise((resolve) => setTimeout(resolve, delay));
        answerSentAt = Date.now();
        send("SUBMIT_ANSWER", KNOWN_TRACK_TITLE);
      } else if (strategy === "wrong_then_timeout") {
        send("SUBMIT_ANSWER", "definitely not the title");
      } else {
        send("SKIP_ROUND");
      }

      const revealTimeoutMs = strategy === "wrong_then_timeout" || strategy === "skip"
        ? roundDurationMs + config.commandTimeoutMs
        : config.commandTimeoutMs;
      const revealed = await publicEvents.waitFor(
        (item) => item.event.type === "ROUND_REVEALED" && item.event.payload.roundIndex === roundIndex,
        revealTimeoutMs,
        `ROUND_REVEALED[${roundIndex}]`,
      );
      const points = revealed.event.payload.roundPoints as number;
      const actualOutcome = revealed.event.payload.outcome as string;
      const terminalLatencyMs = revealed.receivedAt - (answerSentAt ?? startsAtMs);

      let scoreWithinExpectedRange = true;
      if (expectedOutcome === "TITLE_FOUND") {
        const upperBound = expectedScoreUpperBound((answerSentAt ?? Date.now()) - startsAtMs, roundDurationMs);
        scoreWithinExpectedRange = points >= 100 && points <= upperBound + 20;
      } else {
        scoreWithinExpectedRange = points === 0;
      }
      if (actualOutcome !== expectedOutcome) {
        errors.push(`round ${roundIndex}: expected outcome ${expectedOutcome}, got ${actualOutcome}`);
      }
      if (!scoreWithinExpectedRange) {
        errors.push(`round ${roundIndex}: score ${points} outside expected range for ${expectedOutcome}`);
      }

      rounds.push({
        roundIndex,
        strategy,
        expectedOutcome,
        actualOutcome,
        points,
        scoreWithinExpectedRange,
        terminalLatencyMs,
      });
    }

    const finished = await publicEvents.waitFor(
      (item) => item.event.type === "GAME_FINISHED",
      roundDurationMs + config.commandTimeoutMs,
      "GAME_FINISHED",
    );
    const finalScore = finished.event.payload.totalScore as number;

    const errorEvents = privateEvents.all((item) => item.event.type === "ERROR");
    if (errorEvents.length > 0) {
      errors.push(`received ${errorEvents.length} private ERROR event(s)`);
    }

    client.deactivate();

    const summary = await awaitSummary(token, session.sessionId);
    const summaryScoreMatches = summary.totalScore === finalScore;
    const summaryOutcomesMatch = summary.rounds.every(
      (round, index) => round.outcome === rounds[index]?.actualOutcome
        && round.points === rounds[index]?.points,
    );
    if (!summaryScoreMatches) {
      errors.push(`summary total score ${summary.totalScore} != live final score ${finalScore}`);
    }
    if (!summaryOutcomesMatch) {
      errors.push("summary round outcomes/points do not match what was played live");
    }

    return {
      sessionId: session.sessionId,
      ok: errors.length === 0,
      errors,
      rounds,
      privateErrorCount: errorEvents.length,
      wallClockMs: Date.now() - startedAt,
      finalScore,
      summary,
      summaryReconciled: summaryScoreMatches && summaryOutcomesMatch,
    };
  } catch (cause) {
    client.deactivate();
    errors.push(cause instanceof Error ? cause.message : String(cause));
    return {
      sessionId: session.sessionId,
      ok: false,
      errors,
      rounds,
      privateErrorCount: 0,
      wallClockMs: Date.now() - startedAt,
      finalScore: 0,
      summaryReconciled: false,
    };
  }
}
